package com.foldersync.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.foldersync.domain.sync.ConflictResolutionStrategy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scheduler for managing sync work requests
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager
) {

    companion object {
        private const val DEFAULT_SYNC_INTERVAL_MINUTES = 60L
        private const val MIN_WORKMANAGER_INTERVAL_MINUTES = 15L
        private const val WORK_NAME_DEBUG_SYNC = "debug_periodic_sync"
    }

    /**
     * Schedule periodic sync with interval in MINUTES.
     * For intervals < 15 minutes (debug mode), uses repeated OneTimeWork instead of PeriodicWork.
     */
    fun schedulePeriodicSync(
        localFolderUri: String,
        driveFolderId: String,
        intervalMinutes: Long = DEFAULT_SYNC_INTERVAL_MINUTES,
        requiresWifi: Boolean = false,
        requiresCharging: Boolean = false,
        conflictStrategy: ConflictResolutionStrategy = ConflictResolutionStrategy.KEEP_REMOTE
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (requiresWifi) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .setRequiresCharging(requiresCharging)
            .setRequiresBatteryNotLow(true)
            .build()

        val inputData = Data.Builder()
            .putString(SyncWorker.KEY_LOCAL_FOLDER_URI, localFolderUri)
            .putString(SyncWorker.KEY_DRIVE_FOLDER_ID, driveFolderId)
            .putString(SyncWorker.KEY_CONFLICT_STRATEGY, conflictStrategy.name)
            .putBoolean(SyncWorker.KEY_IS_MANUAL, false)
            .putLong(SyncWorker.KEY_DEBUG_INTERVAL_MINUTES, intervalMinutes) // For re-scheduling
            .putBoolean(SyncWorker.KEY_REQUIRES_WIFI, requiresWifi)
            .putBoolean(SyncWorker.KEY_REQUIRES_CHARGING, requiresCharging)
            .build()

        // For short intervals (debug mode), use OneTimeWork with delay
        if (intervalMinutes < MIN_WORKMANAGER_INTERVAL_MINUTES) {
            android.util.Log.d("SyncScheduler", "DEBUG MODE: Scheduling sync every $intervalMinutes minutes using OneTimeWork")
            
            // Cancel any existing periodic work
            workManager.cancelUniqueWork(SyncWorker.WORK_NAME_PERIODIC)
            
            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .setInitialDelay(intervalMinutes, TimeUnit.MINUTES)
                .addTag("sync")
                .addTag("debug_periodic")
                .build()
            
            workManager.enqueueUniqueWork(
                WORK_NAME_DEBUG_SYNC,
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )
            android.util.Log.d("SyncScheduler", "Debug sync enqueued, will run in $intervalMinutes minutes")
            return
        }

        // Cancel debug sync if switching to normal periodic
        workManager.cancelUniqueWork(WORK_NAME_DEBUG_SYNC)
        
        android.util.Log.d("SyncScheduler", "Scheduling periodic sync every $intervalMinutes minutes (wifi=$requiresWifi, charging=$requiresCharging)")
        
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalMinutes, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES // Flex interval of 5 minutes
        )
            .setConstraints(constraints)
            .setInputData(inputData)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .addTag("sync")
            .addTag("periodic")
            .build()

        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest
        )
        android.util.Log.d("SyncScheduler", "Periodic sync enqueued with workName=${SyncWorker.WORK_NAME_PERIODIC}")
    }

    /**
     * Cancel periodic sync
     */
    fun cancelPeriodicSync() {
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME_PERIODIC)
    }

    /**
     * Trigger immediate manual sync
     */
    fun triggerManualSync(
        localFolderUri: String? = null,
        driveFolderId: String? = null,
        conflictStrategy: ConflictResolutionStrategy = ConflictResolutionStrategy.KEEP_REMOTE
    ) {
        android.util.Log.d("SyncScheduler", "triggerManualSync called")
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputDataBuilder = Data.Builder()
            .putBoolean(SyncWorker.KEY_IS_MANUAL, true)
            .putString(SyncWorker.KEY_CONFLICT_STRATEGY, conflictStrategy.name)
        
        localFolderUri?.let { 
            inputDataBuilder.putString(SyncWorker.KEY_LOCAL_FOLDER_URI, it) 
        }
        driveFolderId?.let { 
            inputDataBuilder.putString(SyncWorker.KEY_DRIVE_FOLDER_ID, it) 
        }

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInputData(inputDataBuilder.build())
            .addTag("sync")
            .addTag("manual")
            .build()

        workManager.enqueueUniqueWork(
            SyncWorker.WORK_NAME_MANUAL,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
        android.util.Log.d("SyncScheduler", "Manual sync enqueued")
    }

    /**
     * Cancel manual sync
     */
    fun cancelManualSync() {
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME_MANUAL)
    }

    /**
     * Cancel all sync work
     */
    fun cancelAllSync() {
        workManager.cancelAllWorkByTag("sync")
    }

    /**
     * Get sync work status
     */
    fun getSyncStatus(): Flow<SyncWorkStatus> {
        return workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME_PERIODIC)
            .map { workInfos ->
                val workInfo = workInfos.firstOrNull()
                when (workInfo?.state) {
                    WorkInfo.State.RUNNING -> SyncWorkStatus.RUNNING
                    WorkInfo.State.ENQUEUED -> SyncWorkStatus.SCHEDULED
                    WorkInfo.State.SUCCEEDED -> SyncWorkStatus.COMPLETED
                    WorkInfo.State.FAILED -> SyncWorkStatus.FAILED
                    WorkInfo.State.CANCELLED -> SyncWorkStatus.CANCELLED
                    WorkInfo.State.BLOCKED -> SyncWorkStatus.BLOCKED
                    null -> SyncWorkStatus.NOT_SCHEDULED
                }
            }
    }

    /**
     * Get manual sync status
     */
    fun getManualSyncStatus(): Flow<SyncWorkStatus> {
        return workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME_MANUAL)
            .map { workInfos ->
                val workInfo = workInfos.firstOrNull()
                when (workInfo?.state) {
                    WorkInfo.State.RUNNING -> SyncWorkStatus.RUNNING
                    WorkInfo.State.ENQUEUED -> SyncWorkStatus.SCHEDULED
                    WorkInfo.State.SUCCEEDED -> SyncWorkStatus.COMPLETED
                    WorkInfo.State.FAILED -> SyncWorkStatus.FAILED
                    WorkInfo.State.CANCELLED -> SyncWorkStatus.CANCELLED
                    WorkInfo.State.BLOCKED -> SyncWorkStatus.BLOCKED
                    null -> SyncWorkStatus.NOT_SCHEDULED
                }
            }
    }

    /**
     * Check if any sync is currently running
     */
    fun isSyncing(): Flow<Boolean> {
        return workManager.getWorkInfosByTagFlow("sync")
            .map { workInfos ->
                workInfos.any { it.state == WorkInfo.State.RUNNING }
            }
    }
}

/**
 * Enum representing sync work status
 */
enum class SyncWorkStatus {
    NOT_SCHEDULED,
    SCHEDULED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    BLOCKED
}
