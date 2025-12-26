package com.foldersync.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.foldersync.data.local.PreferencesManager
import com.foldersync.domain.sync.SyncEngineV2
import com.foldersync.domain.sync.SyncState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Background worker for folder synchronization using WorkManager
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncEngine: SyncEngineV2,
    private val preferencesManager: PreferencesManager,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_LOCAL_FOLDER_URI = "local_folder_uri"
        const val KEY_DRIVE_FOLDER_ID = "drive_folder_id"
        const val KEY_CONFLICT_STRATEGY = "conflict_strategy"
        const val KEY_IS_MANUAL = "is_manual"
        
        const val WORK_NAME_PERIODIC = "periodic_sync"
        const val WORK_NAME_MANUAL = "manual_sync"
        
        const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        val localFolderUri = inputData.getString(KEY_LOCAL_FOLDER_URI)
        val driveFolderId = inputData.getString(KEY_DRIVE_FOLDER_ID)
        @Suppress("UNUSED_VARIABLE")
        val conflictStrategyName = inputData.getString(KEY_CONFLICT_STRATEGY)
        @Suppress("UNUSED_VARIABLE")
        val isManual = inputData.getBoolean(KEY_IS_MANUAL, false)

        // Try to get from preferences if not provided in input
        val effectiveLocalUri = localFolderUri 
            ?: preferencesManager.getLocalFolderUriString().first()
            ?: return Result.failure(createErrorData("No local folder configured"))
        
        val effectiveDriveId = driveFolderId 
            ?: preferencesManager.getDriveFolderIdString().first()
            ?: return Result.failure(createErrorData("No Drive folder configured"))

        // Set foreground for long-running work with notification
        setForeground(createForegroundInfo())

        return try {
            // Execute sync and get result
            val syncResult = syncEngine.sync(
                localFolderUri = Uri.parse(effectiveLocalUri),
                driveFolderId = effectiveDriveId
            )
            
            val filesProcessed = syncResult.filesUploaded + syncResult.filesDownloaded

            if (syncResult.success) {
                notificationHelper.showSyncComplete(filesProcessed)
                preferencesManager.setLastSyncTime(System.currentTimeMillis())
                Result.success(createSuccessData(filesProcessed))
            } else {
                notificationHelper.showSyncError(syncResult.message ?: "Sync failed")
                Result.failure(createErrorData(syncResult.message ?: "Sync failed"))
            }
        } catch (e: Exception) {
            notificationHelper.showSyncError(e.message ?: "Unknown error")
            Result.failure(createErrorData(e.message ?: "Unknown error"))
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val notification = notificationHelper.createSyncProgressNotification(
            processedFiles = 0,
            totalFiles = 0,
            currentFile = "Starting sync..."
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createSuccessData(filesProcessed: Int): Data {
        return Data.Builder()
            .putInt("files_processed", filesProcessed)
            .putLong("completed_at", System.currentTimeMillis())
            .build()
    }

    private fun createErrorData(message: String): Data {
        return Data.Builder()
            .putString("error_message", message)
            .putLong("failed_at", System.currentTimeMillis())
            .build()
    }
}
