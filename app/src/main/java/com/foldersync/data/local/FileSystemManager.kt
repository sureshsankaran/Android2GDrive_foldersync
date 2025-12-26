package com.foldersync.data.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

@Singleton
class FileSystemManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val checksumCalculator: ChecksumCalculator
) {

    fun createFolderPickerIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
        }
    }

    fun takePersistableUriPermission(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            // Ignore if permission cannot be persisted
        }
    }

    fun checkPermission(uri: Uri): Boolean {
        return context.contentResolver.persistedUriPermissions.any { permission ->
            val matchesUri = uri.toString().startsWith(permission.uri.toString())
            matchesUri && permission.isReadPermission && permission.isWritePermission
        }
    }

    fun scanFolder(uri: Uri): Flow<LocalFile> = flow {
        val root = DocumentFile.fromTreeUri(context, uri) ?: return@flow
        val rootName = root.name ?: ""
        
        suspend fun traverse(documentFile: DocumentFile, parentPath: String) {
            val relativePath = if (parentPath.isEmpty()) {
                documentFile.name ?: ""
            } else {
                "$parentPath/${documentFile.name ?: ""}"
            }
            emit(buildLocalFile(documentFile, relativePath))
            if (documentFile.isDirectory) {
                for (child in documentFile.listFiles()) {
                    traverse(child, relativePath)
                }
            }
        }
        
        // Emit root folder
        emit(buildLocalFile(root, ""))
        
        // Traverse children
        for (child in root.listFiles()) {
            traverse(child, "")
        }
    }.flowOn(Dispatchers.IO)

    fun getFileMetadata(uri: Uri): LocalFile {
        val documentFile = DocumentFile.fromSingleUri(context, uri)
            ?: DocumentFile.fromTreeUri(context, uri)
            ?: throw IllegalArgumentException("Unable to resolve DocumentFile for $uri")
        return buildLocalFile(documentFile)
    }

    fun readFileContent(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Unable to open input stream for $uri")

    fun writeFileContent(uri: Uri, content: InputStream) {
        val outputStream = context.contentResolver.openOutputStream(uri, "w")
            ?: throw IllegalArgumentException("Unable to open output stream for $uri")
        outputStream.use { out ->
            content.use { input ->
                input.copyTo(out)
            }
        }
    }

    fun deleteFile(uri: Uri): Boolean {
        val documentFile = DocumentFile.fromSingleUri(context, uri)
            ?: DocumentFile.fromTreeUri(context, uri)
        return documentFile?.delete() ?: false
    }

    fun createFile(parentUri: Uri, fileName: String, mimeType: String): Uri? {
        val parent = DocumentFile.fromTreeUri(context, parentUri)
            ?: DocumentFile.fromSingleUri(context, parentUri)
            ?: return null
        
        // Check if file already exists - return existing file URI to overwrite
        val existingFile = parent.listFiles().find { it.name == fileName && !it.isDirectory }
        if (existingFile != null) {
            return existingFile.uri
        }
        
        return parent.createFile(mimeType, fileName)?.uri
    }

    private fun buildLocalFile(documentFile: DocumentFile, relativePath: String = ""): LocalFile {
        val isDirectory = documentFile.isDirectory
        val mimeType = if (isDirectory) {
            DocumentsContract.Document.MIME_TYPE_DIR
        } else {
            documentFile.type ?: "application/octet-stream"
        }
        val checksum = if (isDirectory) {
            null
        } else {
            checksumCalculator.calculateMD5(documentFile.uri)
        }
        return LocalFile(
            uri = documentFile.uri,
            name = documentFile.name.orEmpty(),
            path = documentFile.uri.path ?: documentFile.uri.toString(),
            relativePath = relativePath,
            size = documentFile.length(),
            mimeType = mimeType,
            isDirectory = isDirectory,
            lastModified = documentFile.lastModified(),
            checksum = checksum
        )
    }
    
    fun createFolder(parentUri: Uri, folderName: String): Uri? {
        val parent = DocumentFile.fromTreeUri(context, parentUri)
            ?: DocumentFile.fromSingleUri(context, parentUri)
            ?: return null
        return parent.createDirectory(folderName)?.uri
    }
    
    fun findOrCreatePath(rootUri: Uri, relativePath: String): Uri? {
        if (relativePath.isEmpty()) return rootUri
        
        var current = DocumentFile.fromTreeUri(context, rootUri) ?: return null
        val parts = relativePath.split("/").filter { it.isNotEmpty() }
        
        // Create all folders in the path
        for (part in parts) {
            val existing = current.listFiles().find { it.name == part && it.isDirectory }
            current = existing ?: (current.createDirectory(part) ?: return null)
        }
        
        return current.uri
    }

    /**
     * Find a file or folder at the given relative path from root.
     * Returns null if not found.
     */
    fun findFile(rootUri: Uri, relativePath: String): Uri? {
        if (relativePath.isEmpty()) return rootUri
        
        var current: DocumentFile = DocumentFile.fromTreeUri(context, rootUri) ?: return null
        val parts = relativePath.split("/").filter { it.isNotEmpty() }
        
        for ((index, part) in parts.withIndex()) {
            val isLast = index == parts.lastIndex
            val child = current.listFiles().find { it.name == part }
            if (child == null) {
                return null
            }
            current = child
        }
        
        return current.uri
    }
}
