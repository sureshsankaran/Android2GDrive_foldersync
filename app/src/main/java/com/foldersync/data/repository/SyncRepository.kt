package com.foldersync.data.repository

import com.foldersync.data.local.entity.SyncFileEntity
import com.foldersync.data.local.entity.SyncHistoryEntity
import com.foldersync.domain.model.SyncAction
import com.foldersync.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    // File operations
    fun getAllFiles(): Flow<List<SyncFileEntity>>
    suspend fun getFileByLocalPath(localPath: String): SyncFileEntity?
    suspend fun getFileByDriveId(driveFileId: String): SyncFileEntity?
    suspend fun saveFile(file: SyncFileEntity)
    suspend fun saveFiles(files: List<SyncFileEntity>)
    suspend fun deleteFile(file: SyncFileEntity)
    suspend fun deleteFileByLocalPath(localPath: String)
    suspend fun updateFileStatus(localPath: String, status: SyncStatus)
    suspend fun clearAllFiles()

    // Pending operations
    suspend fun getPendingUploads(): List<SyncFileEntity>
    suspend fun getPendingDownloads(): List<SyncFileEntity>
    fun getConflicts(): Flow<List<SyncFileEntity>>
    fun getConflictCount(): Flow<Int>

    // History operations
    fun getRecentHistory(limit: Int = 50): Flow<List<SyncHistoryEntity>>
    fun getHistoryForFile(filePath: String): Flow<List<SyncHistoryEntity>>
    suspend fun logSyncAction(
        action: SyncAction,
        filePath: String,
        fileName: String,
        success: Boolean,
        errorMessage: String? = null,
        bytesTransferred: Long = 0,
        durationMs: Long = 0
    )
    suspend fun clearOldHistory(olderThanDays: Int = 30)

    // Stats
    suspend fun getFileCount(): Int
    suspend fun getSyncedCount(): Int
    suspend fun getErrorCount(): Int
}
