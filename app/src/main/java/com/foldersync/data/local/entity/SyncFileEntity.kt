package com.foldersync.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.foldersync.domain.model.SyncStatus

@Entity(
    tableName = "sync_files",
    indices = [
        Index(value = ["driveFileId"]),
        Index(value = ["syncStatus"]),
        Index(value = ["fileName"])
    ]
)
data class SyncFileEntity(
    @PrimaryKey
    val localPath: String,
    val driveFileId: String? = null,
    val fileName: String,
    val isDirectory: Boolean = false,
    val fileSize: Long = 0,
    val localModifiedTime: Long,
    val driveModifiedTime: Long? = null,
    val localChecksum: String? = null,
    val driveChecksum: String? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val lastSyncTime: Long? = null,
    val mimeType: String? = null,
    val parentPath: String? = null
)
