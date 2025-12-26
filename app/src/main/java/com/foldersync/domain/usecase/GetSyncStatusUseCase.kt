package com.foldersync.domain.usecase

import com.foldersync.data.local.db.SyncFileDao
import com.foldersync.data.local.db.SyncHistoryDao
import com.foldersync.data.local.entity.SyncFileEntity
import com.foldersync.data.local.entity.SyncHistoryEntity
import com.foldersync.domain.model.SyncStatus as SyncStatusEnum
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Data class representing overall sync status summary
 */
data class SyncStatusSummary(
    val totalFiles: Int,
    val syncedFiles: Int,
    val pendingFiles: Int,
    val conflictFiles: Int,
    val lastSyncTime: Long?,
    val lastSyncResult: String?,
    val isSyncing: Boolean
)

/**
 * Data class for detailed file sync info
 */
data class FileSyncInfo(
    val localPath: String,
    val driveId: String?,
    val syncStatus: SyncStatusEnum,
    val lastSyncedAt: Long?,
    val localChecksum: String?,
    val driveChecksum: String?
)

/**
 * Use case for getting sync status and history
 */
class GetSyncStatusUseCase @Inject constructor(
    private val syncFileDao: SyncFileDao,
    private val syncHistoryDao: SyncHistoryDao
) {
    /**
     * Get overall sync status as Flow
     */
    fun getSyncStatusFlow(): Flow<SyncStatusSummary> {
        return combine(
            syncFileDao.getAllFiles(),
            syncHistoryDao.getRecentHistory(1)
        ) { files, recentHistory ->
            calculateSyncStatus(files, recentHistory.firstOrNull())
        }
    }

    /**
     * Get sync history
     */
    fun getSyncHistory(limit: Int = 50): Flow<List<SyncHistoryEntity>> {
        return syncHistoryDao.getRecentHistory(limit)
    }

    /**
     * Get files with conflicts
     */
    fun getConflicts(): Flow<List<FileSyncInfo>> {
        return syncFileDao.getConflicts().map { files ->
            files.map { file ->
                FileSyncInfo(
                    localPath = file.localPath,
                    driveId = file.driveFileId,
                    syncStatus = file.syncStatus,
                    lastSyncedAt = file.lastSyncTime,
                    localChecksum = file.localChecksum,
                    driveChecksum = file.driveChecksum
                )
            }
        }
    }

    /**
     * Get conflict count
     */
    fun getConflictCount(): Flow<Int> {
        return syncFileDao.getConflictCount()
    }

    /**
     * Get recent errors
     */
    fun getRecentErrors(limit: Int = 10): Flow<List<SyncHistoryEntity>> {
        return syncHistoryDao.getRecentErrors(limit)
    }

    private fun calculateSyncStatus(
        files: List<SyncFileEntity>,
        latestHistory: SyncHistoryEntity?
    ): SyncStatusSummary {
        val total = files.size
        val synced = files.count { it.syncStatus == SyncStatusEnum.SYNCED }
        val pending = files.count { 
            it.syncStatus == SyncStatusEnum.LOCAL_ONLY || 
            it.syncStatus == SyncStatusEnum.DRIVE_ONLY ||
            it.syncStatus == SyncStatusEnum.LOCAL_MODIFIED ||
            it.syncStatus == SyncStatusEnum.DRIVE_MODIFIED ||
            it.syncStatus == SyncStatusEnum.PENDING_UPLOAD ||
            it.syncStatus == SyncStatusEnum.PENDING_DOWNLOAD
        }
        val conflict = files.count { it.syncStatus == SyncStatusEnum.CONFLICT }

        return SyncStatusSummary(
            totalFiles = total,
            syncedFiles = synced,
            pendingFiles = pending,
            conflictFiles = conflict,
            lastSyncTime = latestHistory?.timestamp,
            lastSyncResult = latestHistory?.status,
            isSyncing = false // Will be updated by SyncEngine state
        )
    }
}
