package com.foldersync.domain.model

enum class SyncStatus {
    SYNCED,
    LOCAL_MODIFIED,
    DRIVE_MODIFIED,
    CONFLICT,
    LOCAL_ONLY,
    DRIVE_ONLY,
    PENDING_UPLOAD,
    PENDING_DOWNLOAD,
    UPLOADING,
    DOWNLOADING,
    ERROR
}

enum class SyncAction {
    UPLOAD,
    DOWNLOAD,
    DELETE_LOCAL,
    DELETE_DRIVE,
    CONFLICT_RESOLVED,
    SKIP
}
