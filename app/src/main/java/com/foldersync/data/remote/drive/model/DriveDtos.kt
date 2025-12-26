package com.foldersync.data.remote.drive.model

import com.google.gson.annotations.SerializedName

data class DriveFileDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("mimeType") val mimeType: String,
    @SerializedName("modifiedTime") val modifiedTime: String?,
    @SerializedName("size") val size: Long?,
    @SerializedName("md5Checksum") val md5Checksum: String?,
    @SerializedName("parents") val parents: List<String>?,
    @SerializedName("version") val version: Long?
)

data class DriveFileListResponse(
    @SerializedName("files") val files: List<DriveFileDto>,
    @SerializedName("nextPageToken") val nextPageToken: String?
)

data class CreateFolderRequest(
    @SerializedName("name") val name: String,
    @SerializedName("mimeType") val mimeType: String = "application/vnd.google-apps.folder",
    @SerializedName("parents") val parents: List<String>? = null
)

data class DriveFileMetadataRequest(
    @SerializedName("name") val name: String? = null,
    @SerializedName("mimeType") val mimeType: String? = null,
    @SerializedName("parents") val parents: List<String>? = null,
    @SerializedName("trashed") val trashed: Boolean? = null
)
