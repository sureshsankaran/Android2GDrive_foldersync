package com.foldersync.domain.sync

/**
 * Re-export ConflictResolutionStrategy from domain.model for convenience
 */
typealias ConflictResolutionStrategy = com.foldersync.domain.model.ConflictResolutionStrategy

/**
 * Represents the current state of synchronization
 */
enum class SyncState {
    IDLE,
    SCANNING,
    COMPARING,
    SYNCING,
    COMPLETED,
    ERROR,
    CANCELLED
}

/**
 * Detailed sync progress information
 */
data class SyncProgress(
    val state: SyncState = SyncState.IDLE,
    val currentFile: String? = null,
    val processedFiles: Int = 0,
    val totalFiles: Int = 0,
    val uploadedCount: Int = 0,
    val downloadedCount: Int = 0,
    val conflictCount: Int = 0,
    val errorCount: Int = 0,
    val message: String? = null
) {
    val progressPercent: Float
        get() = if (totalFiles > 0) processedFiles.toFloat() / totalFiles else 0f
    
    val isActive: Boolean
        get() = state in listOf(SyncState.SCANNING, SyncState.COMPARING, SyncState.SYNCING)
    
    // Aliases for backwards compatibility
    val processedItems: Int get() = processedFiles
    val totalItems: Int get() = totalFiles
    val currentItem: String? get() = currentFile
}

/**
 * Result of a sync operation
 */
data class SyncResult(
    val success: Boolean,
    val filesUploaded: Int = 0,
    val filesDownloaded: Int = 0,
    val filesDeleted: Int = 0,
    val conflicts: List<ConflictInfo> = emptyList(),
    val errors: List<SyncError> = emptyList(),
    val durationMs: Long = 0,
    val message: String? = null
) {
    val hasConflicts: Boolean get() = conflicts.isNotEmpty()
    val hasErrors: Boolean get() = errors.isNotEmpty()
    val totalChanges: Int get() = filesUploaded + filesDownloaded + filesDeleted
}

/**
 * Information about a file conflict
 */
data class ConflictInfo(
    val localPath: String,
    val driveFileId: String?,
    val fileName: String,
    val localModifiedTime: Long,
    val driveModifiedTime: Long,
    val localSize: Long,
    val driveSize: Long,
    val localChecksum: String?,
    val driveChecksum: String?
)

/**
 * Information about a sync error
 */
data class SyncError(
    val filePath: String,
    val fileName: String,
    val errorType: SyncErrorType,
    val message: String,
    val cause: Throwable? = null
)

enum class SyncErrorType {
    NETWORK_ERROR,
    AUTH_ERROR,
    PERMISSION_DENIED,
    FILE_NOT_FOUND,
    QUOTA_EXCEEDED,
    UNKNOWN
}
