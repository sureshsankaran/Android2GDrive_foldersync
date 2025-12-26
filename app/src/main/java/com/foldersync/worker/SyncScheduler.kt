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
        private const val MIN_SYNC_INTERVAL_MINUTES = 15L
    }

    /**
     * Schedule periodic sync with interval in MINUTES
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
            .build()

        // WorkManager minimum is 15 minutes
        val effectiveInterval = intervalMinutes.coerceAtLeast(MIN_SYNC_INTERVAL_MINUTES)
        android.util.Log.d("SyncScheduler", "Scheduling periodic sync every $effectiveInterval minutes")
        
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            effectiveInterval, TimeUnit.MINUTES,
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
