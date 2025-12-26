package com.foldersync

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.foldersync.data.local.PreferencesManager
import com.foldersync.worker.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FolderSyncApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    @Inject
    lateinit var syncScheduler: SyncScheduler
    
    @Inject
    lateinit var preferencesManager: PreferencesManager
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        // Reschedule sync based on saved preferences when app starts
        reschedulePeriodicSync()
    }
    
    private fun reschedulePeriodicSync() {
        applicationScope.launch {
            try {
                val isAutoSyncEnabled = preferencesManager.isAutoSyncEnabled().first()
                
                if (isAutoSyncEnabled) {
                    val localFolderUri = preferencesManager.getLocalFolderUriString().first() ?: return@launch
                    val driveFolderId = preferencesManager.getDriveFolderIdString().first() ?: return@launch
                    val syncIntervalMinutes = preferencesManager.syncIntervalMinutes.first()
                    val requiresWifi = preferencesManager.wifiOnly.first()
                    val requiresCharging = preferencesManager.chargingOnly.first()
                    
                    android.util.Log.d("FolderSyncApp", "Rescheduling sync on app start: interval=$syncIntervalMinutes min")
                    
                    syncScheduler.schedulePeriodicSync(
                        localFolderUri = localFolderUri,
                        driveFolderId = driveFolderId,
                        intervalMinutes = syncIntervalMinutes.toLong(),
                        requiresWifi = requiresWifi,
                        requiresCharging = requiresCharging
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("FolderSyncApp", "Failed to reschedule sync: ${e.message}")
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
