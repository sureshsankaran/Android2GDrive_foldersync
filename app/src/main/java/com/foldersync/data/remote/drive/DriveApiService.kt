package com.foldersync.data.remote.drive

import com.foldersync.data.remote.drive.model.CreateFolderRequest
import com.foldersync.data.remote.drive.model.DriveFileDto
import com.foldersync.data.remote.drive.model.DriveFileListResponse
import com.foldersync.data.remote.drive.model.DriveFileMetadataRequest
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Part
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface DriveApiService {

    @GET("files")
    suspend fun listFiles(
        @Query("q") query: String,
        @Query("fields") fields: String,
        @Query("orderBy") orderBy: String? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("pageSize") pageSize: Int = 1000,
        @Query("spaces") spaces: String = "drive"
    ): Response<DriveFileListResponse>

    @GET("files/{fileId}")
    suspend fun getFileMetadata(
        @retrofit2.http.Path("fileId") fileId: String,
        @Query("fields") fields: String
    ): Response<DriveFileDto>

    @Streaming
    @GET("files/{fileId}")
    suspend fun downloadFile(
        @retrofit2.http.Path("fileId") fileId: String,
        @Query("alt") alt: String = "media",
        @retrofit2.http.Header("Range") range: String? = null
    ): Response<ResponseBody>

    @Multipart
    @POST
    suspend fun uploadMultipart(
        @Url uploadUrl: String,
        @Part metadata: MultipartBody.Part,
        @Part content: MultipartBody.Part
    ): Response<DriveFileDto>

    @POST
    suspend fun startResumableUpload(
        @Url uploadUrl: String,
        @Body metadata: DriveFileMetadataRequest
    ): Response<Unit>

    @PATCH("files/{fileId}")
    suspend fun updateFileMetadata(
        @retrofit2.http.Path("fileId") fileId: String,
        @Body metadata: DriveFileMetadataRequest,
        @Query("fields") fields: String
    ): Response<DriveFileDto>

    @PATCH
    suspend fun updateFileContent(
        @Url url: String,
        @Body body: okhttp3.RequestBody
    ): Response<DriveFileDto>

    @POST("files")
    suspend fun createFolder(
        @Body request: CreateFolderRequest,
        @Query("fields") fields: String
    ): Response<DriveFileDto>

    @DELETE("files/{fileId}")
    suspend fun deleteFile(
        @retrofit2.http.Path("fileId") fileId: String
    ): Response<Unit>
}
