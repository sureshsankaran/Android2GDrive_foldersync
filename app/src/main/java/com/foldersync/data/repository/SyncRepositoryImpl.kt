package com.foldersync.data.repository

import com.foldersync.data.local.db.SyncFileDao
import com.foldersync.data.local.db.SyncHistoryDao
import com.foldersync.data.local.entity.SyncFileEntity
import com.foldersync.data.local.entity.SyncHistoryEntity
import com.foldersync.domain.model.SyncAction
import com.foldersync.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val syncFileDao: SyncFileDao,
    private val syncHistoryDao: SyncHistoryDao
) : SyncRepository {

    // File operations
    override fun getAllFiles(): Flow<List<SyncFileEntity>> {
        return syncFileDao.getAllFiles()
    }

    override suspend fun getAllTrackedFiles(): List<SyncFileEntity> {
        return syncFileDao.getAllTrackedFiles()
    }

    override suspend fun getFileByLocalPath(localPath: String): SyncFileEntity? {
        return syncFileDao.getByLocalPath(localPath)
    }

    override suspend fun getFileByDriveId(driveFileId: String): SyncFileEntity? {
        return syncFileDao.getByDriveId(driveFileId)
    }

    override suspend fun saveFile(file: SyncFileEntity) {
        syncFileDao.insert(file)
    }

    override suspend fun saveFiles(files: List<SyncFileEntity>) {
        syncFileDao.insertAll(files)
    }

    override suspend fun deleteFile(file: SyncFileEntity) {
        syncFileDao.delete(file)
    }

    override suspend fun deleteFileByLocalPath(localPath: String) {
        syncFileDao.deleteByLocalPath(localPath)
    }

    override suspend fun updateFileStatus(localPath: String, status: SyncStatus) {
        syncFileDao.updateStatus(localPath, status)
    }

    override suspend fun clearAllFiles() {
        syncFileDao.deleteAll()
    }

    // Pending operations
    override suspend fun getPendingUploads(): List<SyncFileEntity> {
        return syncFileDao.getByStatuses(
            listOf(SyncStatus.LOCAL_ONLY, SyncStatus.LOCAL_MODIFIED, SyncStatus.PENDING_UPLOAD)
        )
    }

    override suspend fun getPendingDownloads(): List<SyncFileEntity> {
        return syncFileDao.getByStatuses(
            listOf(SyncStatus.DRIVE_ONLY, SyncStatus.DRIVE_MODIFIED, SyncStatus.PENDING_DOWNLOAD)
        )
    }

    override fun getConflicts(): Flow<List<SyncFileEntity>> {
        return syncFileDao.getConflicts()
    }

    override fun getConflictCount(): Flow<Int> {
        return syncFileDao.getConflictCount()
    }

    // History operations
    override fun getRecentHistory(limit: Int): Flow<List<SyncHistoryEntity>> {
        return syncHistoryDao.getRecentHistory(limit)
    }

    override fun getHistoryForFile(filePath: String): Flow<List<SyncHistoryEntity>> {
        return syncHistoryDao.getHistoryForFile(filePath)
    }

    override suspend fun logSyncAction(
        action: SyncAction,
        filePath: String,
        fileName: String,
        success: Boolean,
        errorMessage: String?,
        bytesTransferred: Long,
        durationMs: Long
    ) {
        val history = SyncHistoryEntity(
            action = action,
            filePath = filePath,
            fileName = fileName,
            status = if (success) "SUCCESS" else "ERROR",
            errorMessage = errorMessage,
            bytesTransferred = bytesTransferred,
            durationMs = durationMs
        )
        syncHistoryDao.insert(history)
    }

    override suspend fun clearOldHistory(olderThanDays: Int) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(olderThanDays.toLong())
        syncHistoryDao.deleteOlderThan(cutoff)
    }

    // Stats
    override suspend fun getFileCount(): Int {
        return syncFileDao.getFileCount()
    }

    override suspend fun getSyncedCount(): Int {
        return syncHistoryDao.getSuccessCount()
    }

    override suspend fun getErrorCount(): Int {
        return syncHistoryDao.getErrorCount()
    }
}
