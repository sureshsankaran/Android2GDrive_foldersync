package com.foldersync.data.remote.drive

import android.content.ContentResolver
import android.net.Uri
import com.foldersync.data.remote.drive.error.DriveApiException
import com.foldersync.data.remote.drive.error.RateLimitException
import com.foldersync.data.remote.drive.model.CreateFolderRequest
import com.foldersync.data.remote.drive.model.DriveFileDto
import com.foldersync.data.remote.drive.model.DriveFileMetadataRequest
import com.foldersync.data.remote.drive.upload.ProgressRequestBody
import com.foldersync.data.repository.AuthRepository
import com.foldersync.domain.model.DriveFile
import com.google.gson.Gson
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okio.BufferedSink
import okio.buffer
import okio.sink
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class DriveFileManager @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val driveApiService: DriveApiService,
    private val rateLimiter: RateLimiter,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    @Suppress("UnusedPrivateMember") private val authRepository: AuthRepository
) {

    private val contentResolver: ContentResolver = context.contentResolver

    suspend fun listFiles(
        folderId: String,
        orderBy: String? = null,
        mimeType: String? = null,
        pageSize: Int = 200
    ): Flow<List<DriveFile>> = flow {
        val queryParts = mutableListOf<String>()
        queryParts.add("'$folderId' in parents")
        queryParts.add("trashed = false")
        if (!mimeType.isNullOrBlank()) {
            queryParts.add("mimeType = '$mimeType'")
        }
        val query = queryParts.joinToString(" and ")
        var pageToken: String? = null
        do {
            val response = rateLimiter.runWithBackoff("listFiles") {
                val resp = driveApiService.listFiles(
                    query = query,
                    fields = FILE_LIST_FIELDS,
                    orderBy = orderBy,
                    pageToken = pageToken,
                    pageSize = pageSize
                )
                if (resp.code() == 429 || resp.code() == 503) {
                    throw RateLimitException("Rate limited while listing files")
                }
                resp
            }
            if (!response.isSuccessful) {
                throw DriveApiException(
                    "Failed to list files",
                    response.code(),
                    response.errorBody()?.string()
                )
            }
            val body = response.body() ?: throw DriveApiException(
                "Empty response from Drive",
                response.code(),
                response.errorBody()?.string()
            )
            emit(body.files.map { it.toDomain() })
            pageToken = body.nextPageToken
        } while (pageToken != null)
    }

    suspend fun uploadFile(
        localUri: Uri,
        folderId: String,
        onProgress: (uploaded: Long, total: Long) -> Unit = { _, _ -> }
    ): DriveFile {
        val mimeType = contentResolver.getType(localUri) ?: "application/octet-stream"
        val fileName = resolveFileName(localUri) ?: "upload.bin"
        val fileSize = resolveFileSize(localUri)
            ?: throw FileNotFoundException("Unable to determine file size for $localUri")

        val metadata = DriveFileMetadataRequest(
            name = fileName,
            mimeType = mimeType,
            parents = listOf(folderId)
        )

        return if (fileSize <= MULTIPART_THRESHOLD_BYTES) {
            uploadMultipart(localUri, metadata, mimeType, fileSize, onProgress)
        } else {
            uploadResumable(localUri, metadata, mimeType, fileSize, onProgress)
        }
    }

    suspend fun downloadFile(
        fileId: String,
        destinationUri: Uri,
        resumeFromBytes: Long = 0,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ) {
        val metadata = fetchMetadata(fileId)
        val expectedSize = metadata.size ?: -1
        val rangeHeader = if (resumeFromBytes > 0) "bytes=$resumeFromBytes-" else null

        val response = rateLimiter.runWithBackoff("downloadFile") {
            val resp = driveApiService.downloadFile(fileId, range = rangeHeader)
            if (resp.code() == 429 || resp.code() == 503) {
                throw RateLimitException("Rate limited while downloading")
            }
            if (!resp.isSuccessful) {
                throw DriveApiException(
                    "Download failed",
                    resp.code(),
                    resp.errorBody()?.string()
                )
            }
            resp
        }

        val body = response.body() ?: throw DriveApiException("Empty download body")
        writeResponseBody(destinationUri, body, resumeFromBytes, expectedSize, onProgress)
        verifyChecksumIfAvailable(destinationUri, metadata.md5Checksum)
    }

    suspend fun updateFile(
        fileId: String,
        localUri: Uri,
        newName: String? = null,
        onProgress: (uploaded: Long, total: Long) -> Unit = { _, _ -> }
    ): DriveFile {
        val mimeType = contentResolver.getType(localUri) ?: "application/octet-stream"
        val fileName = newName ?: resolveFileName(localUri) ?: "updated-file"
        val fileSize = resolveFileSize(localUri)
            ?: throw FileNotFoundException("Unable to determine file size for $localUri")

        val metadata = DriveFileMetadataRequest(name = fileName, mimeType = mimeType)
        val uploadUrl =
            "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=multipart"
        val metadataBody = gson.toJson(metadata)
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val metadataPart = MultipartBody.Part.createFormData("metadata", null, metadataBody)

        val requestBody = ProgressRequestBody(
            contentType = mimeType.toMediaTypeOrNull(),
            contentLength = fileSize,
            inputStreamProvider = { contentResolver.openInputStream(localUri)!! },
            onProgress = onProgress
        )
        val filePart = MultipartBody.Part.createFormData(
            "file",
            fileName,
            requestBody
        )

        val response = rateLimiter.runWithBackoff("updateFile") {
            val resp = driveApiService.uploadMultipart(uploadUrl, metadataPart, filePart)
            if (resp.code() == 429 || resp.code() == 503) {
                throw RateLimitException("Rate limited while updating file")
            }
            resp
        }
        if (!response.isSuccessful) {
            throw DriveApiException(
                "Failed to update file",
                response.code(),
                response.errorBody()?.string()
            )
        }
        val dto = response.body() ?: throw DriveApiException("Empty update response")
        return dto.toDomain()
    }

    suspend fun deleteFile(fileId: String, trashInstead: Boolean = false) {
        if (trashInstead) {
            val update = driveApiService.updateFileMetadata(
                fileId = fileId,
                metadata = DriveFileMetadataRequest(trashed = true),
                fields = METADATA_FIELDS
            )
            if (!update.isSuccessful) {
                throw DriveApiException(
                    "Failed to move file to trash",
                    update.code(),
                    update.errorBody()?.string()
                )
            }
            return
        }

        val response = driveApiService.deleteFile(fileId)
        if (response.code() == 429 || response.code() == 503) {
            throw RateLimitException("Rate limited while deleting file")
        }
        if (!response.isSuccessful) {
            throw DriveApiException(
                "Failed to delete file",
                response.code(),
                response.errorBody()?.string()
            )
        }
    }

    suspend fun createFolder(name: String, parentId: String): DriveFile {
        val response = driveApiService.createFolder(
            request = CreateFolderRequest(name = name, parents = listOf(parentId)),
            fields = METADATA_FIELDS
        )
        if (response.code() == 429 || response.code() == 503) {
            throw RateLimitException("Rate limited while creating folder")
        }
        if (!response.isSuccessful) {
            throw DriveApiException(
                "Failed to create folder",
                response.code(),
                response.errorBody()?.string()
            )
        }
        val dto = response.body() ?: throw DriveApiException("Empty folder response")
        return dto.toDomain()
    }

    /**
     * List only folders within a parent folder
     */
    suspend fun listFolders(parentId: String): List<DriveFile> {
        val query = "'$parentId' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val response = rateLimiter.runWithBackoff("listFolders") {
            val resp = driveApiService.listFiles(
                query = query,
                fields = FILE_LIST_FIELDS,
                orderBy = "name",
                pageSize = 100
            )
            if (resp.code() == 429 || resp.code() == 503) {
                throw RateLimitException("Rate limited while listing folders")
            }
            resp
        }
        if (!response.isSuccessful) {
            throw DriveApiException(
                "Failed to list folders",
                response.code(),
                response.errorBody()?.string()
            )
        }
        val body = response.body() ?: throw DriveApiException("Empty response from Drive")
        return body.files.map { it.toDomain() }
    }

    private suspend fun uploadMultipart(
        localUri: Uri,
        metadata: DriveFileMetadataRequest,
        mimeType: String,
        fileSize: Long,
        onProgress: (Long, Long) -> Unit
    ): DriveFile {
        val metadataJson =
            gson.toJson(metadata).toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val metadataPart = MultipartBody.Part.createFormData("metadata", null, metadataJson)

        val progressBody = ProgressRequestBody(
            contentType = mimeType.toMediaTypeOrNull(),
            contentLength = fileSize,
            inputStreamProvider = { contentResolver.openInputStream(localUri)!! },
            onProgress = onProgress
        )
        val fileName = metadata.name ?: "upload.bin"
        val filePart = MultipartBody.Part.createFormData("file", fileName, progressBody)

        val response = rateLimiter.runWithBackoff("uploadMultipart") {
            val resp = driveApiService.uploadMultipart(MULTIPART_UPLOAD_URL, metadataPart, filePart)
            if (resp.code() == 429 || resp.code() == 503) {
                throw RateLimitException("Rate limited during multipart upload")
            }
            resp
        }
        if (!response.isSuccessful) {
            throw DriveApiException(
                "Multipart upload failed",
                response.code(),
                response.errorBody()?.string()
            )
        }
        val dto = response.body() ?: throw DriveApiException("Empty upload response")
        return dto.toDomain()
    }

    private suspend fun uploadResumable(
        localUri: Uri,
        metadata: DriveFileMetadataRequest,
        mimeType: String,
        fileSize: Long,
        onProgress: (Long, Long) -> Unit
    ): DriveFile {
        val sessionUrl = rateLimiter.runWithBackoff("createResumableSession") {
            createResumableSession(metadata, mimeType, fileSize)
        }
        return uploadInChunks(sessionUrl, localUri, mimeType, fileSize, onProgress)
    }

    private suspend fun createResumableSession(
        metadata: DriveFileMetadataRequest,
        mimeType: String,
        fileSize: Long
    ): String = withContext(Dispatchers.IO) {
        val body = gson.toJson(metadata)
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(RESUMABLE_UPLOAD_URL)
            .addHeader("X-Upload-Content-Type", mimeType)
            .addHeader("X-Upload-Content-Length", fileSize.toString())
            .post(body)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 429 || response.code == 503) {
                throw RateLimitException("Rate limited while creating resumable session")
            }
            if (!response.isSuccessful) {
                throw DriveApiException(
                    "Failed to start resumable upload",
                    response.code,
                    response.body?.string()
                )
            }
            response.header("Location")
                ?: throw DriveApiException("Upload session URL missing")
        }
    }

    private suspend fun uploadInChunks(
        sessionUrl: String,
        localUri: Uri,
        mimeType: String,
        fileSize: Long,
        onProgress: (Long, Long) -> Unit
    ): DriveFile = withContext(Dispatchers.IO) {
        val buffer = ByteArray(CHUNK_SIZE_BYTES.toInt())
        var uploaded = 0L
        contentResolver.openInputStream(localUri)?.use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                val start = uploaded
                val end = uploaded + read - 1
                val contentRange = "bytes $start-$end/$fileSize"

                val requestBody = buffer.copyOf(read)
                    .toRequestBody(mimeType.toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(sessionUrl)
                    .addHeader("Content-Length", read.toString())
                    .addHeader("Content-Type", mimeType)
                    .addHeader("Content-Range", contentRange)
                    .put(requestBody)
                    .build()

                val response = rateLimiter.runWithBackoff("uploadChunk") {
                    val resp = okHttpClient.newCall(request).execute()
                    if (resp.code == 429 || resp.code == 503) {
                        resp.close()
                        throw RateLimitException("Rate limited during chunk upload")
                    }
                    resp
                }
                response.use { resp ->
                    if (resp.code == 308) {
                        // Resume Incomplete, continue sending chunks
                    } else if (!resp.isSuccessful) {
                        throw DriveApiException(
                            "Chunk upload failed",
                            resp.code,
                            resp.body?.string()
                        )
                    } else {
                        val bodyString = resp.body?.string()
                        val dto = gson.fromJson(bodyString, DriveFileDto::class.java)
                        onProgress(fileSize, fileSize)
                        return@withContext dto.toDomain()
                    }
                }
                uploaded += read
                onProgress(min(uploaded, fileSize), fileSize)
            }
        } ?: throw FileNotFoundException("Unable to open input stream for $localUri")
        throw DriveApiException("Resumable upload did not complete")
    }

    private suspend fun fetchMetadata(fileId: String): DriveFileDto {
        val response = driveApiService.getFileMetadata(
            fileId = fileId,
            fields = METADATA_FIELDS
        )
        if (!response.isSuccessful) {
            throw DriveApiException(
                "Failed to fetch metadata",
                response.code(),
                response.errorBody()?.string()
            )
        }
        return response.body() ?: throw DriveApiException("Empty metadata response")
    }

    private suspend fun writeResponseBody(
        destinationUri: Uri,
        body: ResponseBody,
        resumeFromBytes: Long,
        expectedSize: Long,
        onProgress: (Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val outputStream = contentResolver.openOutputStream(
            destinationUri,
            if (resumeFromBytes > 0) "wa" else "w"
        ) ?: throw FileNotFoundException("Cannot open output stream for $destinationUri")

        outputStream.use { output ->
            val sink: BufferedSink = output.sink().buffer()
            val totalBytes = if (expectedSize > 0) expectedSize else body.contentLength()
            var downloaded = resumeFromBytes
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read: Int
            val input = body.byteStream()
            input.use { stream ->
                while (stream.read(buffer).also { read = it } != -1) {
                    sink.write(buffer, 0, read)
                    downloaded += read
                    onProgress(downloaded, totalBytes)
                }
                sink.flush()
            }
        }
    }

    private suspend fun verifyChecksumIfAvailable(destinationUri: Uri, expectedMd5: String?) {
        if (expectedMd5.isNullOrBlank()) return
        val actual = computeMd5(destinationUri)
        if (actual != expectedMd5) {
            throw DriveApiException("Checksum verification failed")
        }
    }

    private suspend fun computeMd5(uri: Uri): String = withContext(Dispatchers.IO) {
        val md = MessageDigest.getInstance("MD5")
        contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                md.update(buffer, 0, read)
            }
        } ?: throw FileNotFoundException("Unable to read for checksum: $uri")
        md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun DriveFileDto.toDomain(): DriveFile {
        val epoch = modifiedTime?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        return DriveFile(
            id = id,
            name = name,
            mimeType = mimeType,
            modifiedTime = epoch,
            size = size,
            md5Checksum = md5Checksum,
            parents = parents,
            version = version
        )
    }

    private fun resolveFileName(uri: Uri): String? {
        val projection = arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return null
    }

    private fun resolveFileSize(uri: Uri): Long? {
        val projection = arrayOf(android.provider.OpenableColumns.SIZE)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (sizeIndex != -1 && cursor.moveToFirst()) {
                return cursor.getLong(sizeIndex)
            }
        }
        return null
    }

    companion object {
        private const val METADATA_FIELDS =
            "id,name,mimeType,modifiedTime,size,md5Checksum,parents,version"
        private const val FILE_LIST_FIELDS = "files($METADATA_FIELDS),nextPageToken"
        private const val MULTIPART_UPLOAD_URL =
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        private const val RESUMABLE_UPLOAD_URL =
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable"
        private const val MULTIPART_THRESHOLD_BYTES = 5 * 1024 * 1024 // 5 MB
        private const val CHUNK_SIZE_BYTES = 2 * 1024 * 1024 // 2 MB
        private const val DEFAULT_BUFFER_SIZE = 8 * 1024
    }
}
