package com.foldersync.data.remote.drive.model

import com.foldersync.domain.model.DriveFile
import com.google.gson.annotations.SerializedName
import java.time.Instant

data class DriveFileDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("mimeType") val mimeType: String,
    @SerializedName("modifiedTime") val modifiedTime: String?,
    @SerializedName("size") val size: Long?,
    @SerializedName("md5Checksum") val md5Checksum: String?,
    @SerializedName("parents") val parents: List<String>?,
    @SerializedName("version") val version: Long?
) {
    /**
     * Convert DTO to domain model with optional relative path
     */
    fun toDomain(relativePath: String = ""): DriveFile {
        val modifiedTimeMillis = modifiedTime?.let {
            try {
                Instant.parse(it).toEpochMilli()
            } catch (e: Exception) {
                null
            }
        }
        return DriveFile(
            id = id,
            name = name,
            mimeType = mimeType,
            modifiedTime = modifiedTimeMillis,
            size = size,
            md5Checksum = md5Checksum,
            parents = parents,
            version = version,
            relativePath = relativePath.ifEmpty { name }
        )
    }
}

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
