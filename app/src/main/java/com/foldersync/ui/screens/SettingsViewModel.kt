package com.foldersync.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldersync.data.local.PreferencesManager
import com.foldersync.domain.sync.ConflictResolutionStrategy
import com.foldersync.worker.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val autoSyncEnabled: Boolean = true,
    val syncIntervalMinutes: Int = 60,
    val wifiOnly: Boolean = false,
    val chargingOnly: Boolean = false,
    val conflictStrategy: ConflictResolutionStrategy = ConflictResolutionStrategy.ASK_USER
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            combine(
                preferencesManager.autoSyncEnabled,
                preferencesManager.syncIntervalMinutes,
                preferencesManager.wifiOnly,
                preferencesManager.chargingOnly,
                preferencesManager.conflictResolutionStrategy
            ) { autoSync, interval, wifi, charging, conflict ->
                SettingsUiState(
                    autoSyncEnabled = autoSync,
                    syncIntervalMinutes = interval,
                    wifiOnly = wifi,
                    chargingOnly = charging,
                    conflictStrategy = conflict
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAutoSyncEnabled(enabled)
            if (enabled) {
                schedulePeriodicSync()
            } else {
                syncScheduler.cancelPeriodicSync()
            }
        }
    }

    fun setSyncInterval(minutes: Int) {
        viewModelScope.launch {
            preferencesManager.setSyncIntervalMinutes(minutes)
            if (_uiState.value.autoSyncEnabled) {
                schedulePeriodicSync()
            }
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setWifiOnly(enabled)
            if (_uiState.value.autoSyncEnabled) {
                schedulePeriodicSync()
            }
        }
    }

    fun setChargingOnly(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setChargingOnly(enabled)
            if (_uiState.value.autoSyncEnabled) {
                schedulePeriodicSync()
            }
        }
    }

    fun setConflictStrategy(strategy: ConflictResolutionStrategy) {
        viewModelScope.launch {
            preferencesManager.setConflictResolutionStrategy(strategy)
        }
    }

    private suspend fun schedulePeriodicSync() {
        val localUri = preferencesManager.getLocalFolderUriString().first()
        if (localUri == null) {
            android.util.Log.w("SettingsViewModel", "Cannot schedule periodic sync: local folder URI not configured")
            return
        }
        
        val driveId = preferencesManager.getDriveFolderIdString().first()
        if (driveId == null) {
            android.util.Log.w("SettingsViewModel", "Cannot schedule periodic sync: Drive folder ID not configured")
            return
        }
        
        val interval = preferencesManager.syncIntervalMinutes.first()
        val wifi = preferencesManager.wifiOnly.first()
        val charging = preferencesManager.chargingOnly.first()

        android.util.Log.d("SettingsViewModel", "Scheduling periodic sync: interval=${interval}min, wifi=$wifi, charging=$charging")
        
        syncScheduler.schedulePeriodicSync(
            localFolderUri = localUri,
            driveFolderId = driveId,
            intervalMinutes = interval.toLong(),
            requiresWifi = wifi,
            requiresCharging = charging
        )
    }
}
