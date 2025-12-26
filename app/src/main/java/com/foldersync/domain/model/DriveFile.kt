package com.foldersync.domain.model

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val modifiedTime: Long?,
    val size: Long?,
    val md5Checksum: String?,
    val parents: List<String>?,
    val version: Long?
)
