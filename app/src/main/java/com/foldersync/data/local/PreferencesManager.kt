package com.foldersync.data.local

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.foldersync.domain.model.ConflictResolutionStrategy
import com.foldersync.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val LOCAL_FOLDER_URI = stringPreferencesKey(Constants.PrefsKeys.LOCAL_FOLDER_URI)
        val DRIVE_FOLDER_ID = stringPreferencesKey(Constants.PrefsKeys.DRIVE_FOLDER_ID)
        val DRIVE_FOLDER_NAME = stringPreferencesKey(Constants.PrefsKeys.DRIVE_FOLDER_NAME)
        val AUTO_SYNC_ENABLED = booleanPreferencesKey(Constants.PrefsKeys.AUTO_SYNC_ENABLED)
        val SYNC_INTERVAL_MINUTES = intPreferencesKey(Constants.PrefsKeys.SYNC_INTERVAL_MINUTES)
        val WIFI_ONLY = booleanPreferencesKey(Constants.PrefsKeys.WIFI_ONLY)
        val CHARGING_ONLY = booleanPreferencesKey("charging_only")
        val LAST_SYNC_TIME = longPreferencesKey(Constants.PrefsKeys.LAST_SYNC_TIME)
        val CONFLICT_RESOLUTION = stringPreferencesKey(Constants.PrefsKeys.CONFLICT_RESOLUTION_STRATEGY)
    }

    // Local folder URI
    val localFolderUri: Flow<Uri?> = dataStore.data.map { prefs ->
        prefs[Keys.LOCAL_FOLDER_URI]?.let { Uri.parse(it) }
    }

    suspend fun setLocalFolderUri(uri: Uri?) {
        dataStore.edit { prefs ->
            if (uri != null) {
                prefs[Keys.LOCAL_FOLDER_URI] = uri.toString()
            } else {
                prefs.remove(Keys.LOCAL_FOLDER_URI)
            }
        }
    }

    // Drive folder
    val driveFolderId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[Keys.DRIVE_FOLDER_ID]
    }

    val driveFolderName: Flow<String?> = dataStore.data.map { prefs ->
        prefs[Keys.DRIVE_FOLDER_NAME]
    }

    suspend fun setDriveFolder(folderId: String?, folderName: String?) {
        dataStore.edit { prefs ->
            if (folderId != null) {
                prefs[Keys.DRIVE_FOLDER_ID] = folderId
            } else {
                prefs.remove(Keys.DRIVE_FOLDER_ID)
            }
            if (folderName != null) {
                prefs[Keys.DRIVE_FOLDER_NAME] = folderName
            } else {
                prefs.remove(Keys.DRIVE_FOLDER_NAME)
            }
        }
    }

    // Auto sync enabled
    val autoSyncEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.AUTO_SYNC_ENABLED] ?: true
    }

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_SYNC_ENABLED] = enabled
        }
    }

    // Sync interval
    val syncIntervalMinutes: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.SYNC_INTERVAL_MINUTES] ?: Constants.DEFAULT_SYNC_INTERVAL_MINUTES
    }

    suspend fun setSyncIntervalMinutes(minutes: Int) {
        val clampedMinutes = minutes.coerceIn(
            Constants.MIN_SYNC_INTERVAL_MINUTES,
            Constants.MAX_SYNC_INTERVAL_MINUTES
        )
        dataStore.edit { prefs ->
            prefs[Keys.SYNC_INTERVAL_MINUTES] = clampedMinutes
        }
    }

    // WiFi only
    val wifiOnly: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.WIFI_ONLY] ?: false
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.WIFI_ONLY] = enabled
        }
    }

    // Charging only
    val chargingOnly: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.CHARGING_ONLY] ?: false
    }

    suspend fun setChargingOnly(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.CHARGING_ONLY] = enabled
        }
    }

    // Last sync time
    val lastSyncTime: Flow<Long?> = dataStore.data.map { prefs ->
        prefs[Keys.LAST_SYNC_TIME]
    }

    suspend fun setLastSyncTime(timestamp: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC_TIME] = timestamp
        }
    }

    // Conflict resolution strategy
    val conflictResolutionStrategy: Flow<ConflictResolutionStrategy> = dataStore.data.map { prefs ->
        prefs[Keys.CONFLICT_RESOLUTION]?.let { 
            ConflictResolutionStrategy.valueOf(it) 
        } ?: ConflictResolutionStrategy.ASK_USER
    }

    suspend fun setConflictResolutionStrategy(strategy: ConflictResolutionStrategy) {
        dataStore.edit { prefs ->
            prefs[Keys.CONFLICT_RESOLUTION] = strategy.name
        }
    }

    // Check if setup is complete
    val isSetupComplete: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.LOCAL_FOLDER_URI] != null && prefs[Keys.DRIVE_FOLDER_ID] != null
    }

    // Clear all preferences
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    // Convenience methods for workers
    fun getLocalFolderUriString(): Flow<String?> = dataStore.data.map { prefs ->
        prefs[Keys.LOCAL_FOLDER_URI]
    }

    fun getDriveFolderIdString(): Flow<String?> = dataStore.data.map { prefs ->
        prefs[Keys.DRIVE_FOLDER_ID]
    }

    fun isAutoSyncEnabled(): Flow<Boolean> = autoSyncEnabled

    fun getSyncInterval(): Flow<Int> = syncIntervalMinutes

    fun isSyncOnWifiOnly(): Flow<Boolean> = wifiOnly

    fun isSyncWhileCharging(): Flow<Boolean> = chargingOnly
}
