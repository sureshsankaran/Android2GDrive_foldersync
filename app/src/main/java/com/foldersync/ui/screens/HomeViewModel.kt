package com.foldersync.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldersync.data.local.PreferencesManager
import com.foldersync.domain.model.SyncStatus
import com.foldersync.util.DateTimeUtils
import com.foldersync.worker.SyncScheduler
import com.foldersync.worker.SyncWorkStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val localFolderUri: Uri? = null,
    val driveFolderName: String? = null,
    val lastSyncTime: String = "Never",
    val syncStatus: SyncStatus = SyncStatus.PENDING_UPLOAD,
    val isSyncing: Boolean = false,
    val pendingConflicts: Int = 0,
    val totalFiles: Int = 0,
    val totalSizeBytes: Long = 0L
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        observePreferences()
        observeSyncStatus()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            combine(
                preferencesManager.localFolderUri,
                preferencesManager.driveFolderName,
                preferencesManager.lastSyncTime
            ) { localFolder, driveFolderName, lastSync ->
                Triple(localFolder, driveFolderName, lastSync)
            }.collect { (localFolder, driveFolderName, lastSync) ->
                _uiState.update {
                    it.copy(
                        localFolderUri = localFolder,
                        driveFolderName = driveFolderName,
                        lastSyncTime = lastSync?.let { ts ->
                            DateTimeUtils.formatRelativeTime(ts)
                        } ?: "Never"
                    )
                }
            }
        }
    }

    private fun observeSyncStatus() {
        viewModelScope.launch {
            syncScheduler.isSyncing().collect { syncing ->
                _uiState.update { 
                    it.copy(
                        isSyncing = syncing,
                        syncStatus = if (syncing) SyncStatus.UPLOADING else it.syncStatus
                    )
                }
            }
        }
    }

    fun triggerSync() {
        if (_uiState.value.isSyncing) return
        syncScheduler.triggerManualSync()
    }

    fun updateConflictCount(count: Int) {
        _uiState.update { it.copy(pendingConflicts = count.coerceAtLeast(0)) }
    }

    fun setLocalFolder(uri: Uri) {
        viewModelScope.launch {
            preferencesManager.setLocalFolderUri(uri)
        }
    }

    fun setDriveFolder(folderId: String?, folderName: String?) {
        viewModelScope.launch {
            preferencesManager.setDriveFolder(folderId, folderName)
        }
    }
}
