package com.foldersync.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.foldersync.data.local.PreferencesManager
import com.foldersync.domain.sync.SyncEngineV2
import com.foldersync.domain.sync.SyncState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

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
        const val KEY_DEBUG_INTERVAL_MINUTES = "debug_interval_minutes"
        const val KEY_REQUIRES_WIFI = "requires_wifi"
        const val KEY_REQUIRES_CHARGING = "requires_charging"
        
        const val WORK_NAME_PERIODIC = "periodic_sync"
        const val WORK_NAME_MANUAL = "manual_sync"
        const val WORK_NAME_DEBUG_SYNC = "debug_periodic_sync"
        
        const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        android.util.Log.d("SyncWorker", "doWork() started - runAttemptCount=${runAttemptCount}")
        
        val localFolderUri = inputData.getString(KEY_LOCAL_FOLDER_URI)
        val driveFolderId = inputData.getString(KEY_DRIVE_FOLDER_ID)
        @Suppress("UNUSED_VARIABLE")
        val conflictStrategyName = inputData.getString(KEY_CONFLICT_STRATEGY)
        @Suppress("UNUSED_VARIABLE")
        val isManual = inputData.getBoolean(KEY_IS_MANUAL, false)

        // Try to get from preferences if not provided in input
        val effectiveLocalUri = localFolderUri 
            ?: preferencesManager.getLocalFolderUriString().first()
            ?: run {
                android.util.Log.e("SyncWorker", "No local folder configured - failing")
                return Result.failure(createErrorData("No local folder configured"))
            }
        
        val effectiveDriveId = driveFolderId 
            ?: preferencesManager.getDriveFolderIdString().first()
            ?: run {
                android.util.Log.e("SyncWorker", "No Drive folder configured - failing")
                return Result.failure(createErrorData("No Drive folder configured"))
            }

        android.util.Log.d("SyncWorker", "Starting sync: localUri=$effectiveLocalUri, driveId=$effectiveDriveId")

        // Try to set foreground for long-running work with notification
        // This can fail on Android 12+ when started from background, so we catch the exception
        try {
            setForeground(createForegroundInfo())
        } catch (e: Exception) {
            android.util.Log.w("SyncWorker", "Could not start foreground service (background start): ${e.message}")
            // Continue without foreground - sync will still work
        }

        return try {
            // Execute sync and get result
            val syncResult = syncEngine.sync(
                localFolderUri = Uri.parse(effectiveLocalUri),
                driveFolderId = effectiveDriveId
            )
            
            val filesProcessed = syncResult.filesUploaded + syncResult.filesDownloaded
            android.util.Log.d("SyncWorker", "Sync completed: success=${syncResult.success}, uploaded=${syncResult.filesUploaded}, downloaded=${syncResult.filesDownloaded}")

            if (syncResult.success) {
                notificationHelper.showSyncComplete(filesProcessed)
                preferencesManager.setLastSyncTime(System.currentTimeMillis())
                scheduleNextDebugSyncIfNeeded()
                Result.success(createSuccessData(filesProcessed))
            } else {
                android.util.Log.e("SyncWorker", "Sync failed: ${syncResult.message}")
                notificationHelper.showSyncError(syncResult.message ?: "Sync failed")
                scheduleNextDebugSyncIfNeeded()
                Result.failure(createErrorData(syncResult.message ?: "Sync failed"))
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Sync exception: ${e.message}", e)
            notificationHelper.showSyncError(e.message ?: "Unknown error")
            scheduleNextDebugSyncIfNeeded()
            Result.failure(createErrorData(e.message ?: "Unknown error"))
        }
    }
    
    /**
     * For debug mode (intervals < 15 min), schedule the next sync run.
     * This simulates periodic behavior using chained OneTimeWorkRequests.
     */
    private fun scheduleNextDebugSyncIfNeeded() {
        val debugInterval = inputData.getLong(KEY_DEBUG_INTERVAL_MINUTES, 0)
        if (debugInterval <= 0 || debugInterval >= 15) return
        
        val requiresWifi = inputData.getBoolean(KEY_REQUIRES_WIFI, false)
        val requiresCharging = inputData.getBoolean(KEY_REQUIRES_CHARGING, false)
        
        android.util.Log.d("SyncWorker", "DEBUG MODE: Scheduling next sync in $debugInterval minutes")
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (requiresWifi) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .setRequiresCharging(requiresCharging)
            // No battery constraint for debug mode - be aggressive
            .build()
        
        val nextSyncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInputData(inputData) // Pass through all the same data
            .setInitialDelay(debugInterval, TimeUnit.MINUTES)
            .addTag("sync")
            .addTag("debug_periodic")
            .build()
        
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            WORK_NAME_DEBUG_SYNC,
            ExistingWorkPolicy.REPLACE,
            nextSyncRequest
        )
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
