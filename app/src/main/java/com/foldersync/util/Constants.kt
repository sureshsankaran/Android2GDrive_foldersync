package com.foldersync.util

object Constants {
    // Sync settings
    const val DEFAULT_SYNC_INTERVAL_MINUTES = 15
    const val MIN_SYNC_INTERVAL_MINUTES = 5
    const val MAX_SYNC_INTERVAL_MINUTES = 1440 // 24 hours

    // File size limits
    const val MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024 * 1024 // 5 GB
    const val LARGE_FILE_THRESHOLD_BYTES = 5L * 1024 * 1024 // 5 MB (use resumable upload)
    const val CHUNK_SIZE_BYTES = 256 * 1024 // 256 KB for chunked uploads

    // Network
    const val CONNECT_TIMEOUT_SECONDS = 30L
    const val READ_TIMEOUT_SECONDS = 60L
    const val WRITE_TIMEOUT_SECONDS = 60L

    // Retry
    const val MAX_RETRY_COUNT = 3
    const val INITIAL_RETRY_DELAY_MS = 1000L

    // History
    const val HISTORY_RETENTION_DAYS = 30
    const val MAX_HISTORY_ITEMS_DISPLAY = 100

    // DataStore keys
    object PrefsKeys {
        const val LOCAL_FOLDER_URI = "local_folder_uri"
        const val DRIVE_FOLDER_ID = "drive_folder_id"
        const val DRIVE_FOLDER_NAME = "drive_folder_name"
        const val AUTO_SYNC_ENABLED = "auto_sync_enabled"
        const val SYNC_INTERVAL_MINUTES = "sync_interval_minutes"
        const val WIFI_ONLY = "wifi_only"
        const val LAST_SYNC_TIME = "last_sync_time"
        const val CONFLICT_RESOLUTION_STRATEGY = "conflict_resolution_strategy"
    }

    // Google Drive
    const val DRIVE_FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
    const val DRIVE_ROOT_FOLDER_ID = "root"

    // Notification
    const val SYNC_NOTIFICATION_CHANNEL_ID = "sync_channel"
    const val SYNC_NOTIFICATION_ID = 1001
    const val ERROR_NOTIFICATION_ID = 1002
}
