package com.foldersync.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.foldersync.data.local.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receiver to re-schedule sync after device boot
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var preferencesManager: PreferencesManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            
            scope.launch {
                try {
                    rescheduleSync()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private suspend fun rescheduleSync() {
        val isAutoSyncEnabled = preferencesManager.isAutoSyncEnabled().first()
        
        if (isAutoSyncEnabled) {
            val localFolderUri = preferencesManager.getLocalFolderUriString().first() ?: return
            val driveFolderId = preferencesManager.getDriveFolderIdString().first() ?: return
            val syncIntervalMinutes = preferencesManager.syncIntervalMinutes.first()
            val requiresWifi = preferencesManager.wifiOnly.first()
            val requiresCharging = preferencesManager.chargingOnly.first()

            syncScheduler.schedulePeriodicSync(
                localFolderUri = localFolderUri,
                driveFolderId = driveFolderId,
                intervalMinutes = syncIntervalMinutes.toLong(),
                requiresWifi = requiresWifi,
                requiresCharging = requiresCharging
            )
        }
    }
}
