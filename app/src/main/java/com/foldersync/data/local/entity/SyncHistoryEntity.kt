package com.foldersync.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.foldersync.domain.model.SyncAction

@Entity(
    tableName = "sync_history",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["filePath"]),
        Index(value = ["action"])
    ]
)
data class SyncHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val action: SyncAction,
    val filePath: String,
    val fileName: String,
    val status: String, // "SUCCESS", "ERROR", "SKIPPED"
    val errorMessage: String? = null,
    val bytesTransferred: Long = 0,
    val durationMs: Long = 0
)
