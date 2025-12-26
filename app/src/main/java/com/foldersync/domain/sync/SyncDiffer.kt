package com.foldersync.domain.sync

import com.foldersync.data.local.LocalFile
import com.foldersync.data.local.entity.SyncFileEntity
import com.foldersync.domain.model.DriveFile
import com.foldersync.domain.model.SyncStatus
import javax.inject.Inject

/**
 * Clean sync logic using database tracking for proper create/update/delete detection.
 * 
 * SYNC LOGIC:
 * 
 * 1. BOTH EXIST (local + drive):
 *    - Compare checksums/timestamps to detect modifications
 *    - If identical: mark as SYNCED
 *    - If local newer: upload
 *    - If remote newer: download
 *    - If both modified since last sync: conflict
 * 
 * 2. LOCAL ONLY (exists locally, not on drive):
 *    - If tracked in DB with driveId: was synced before → deleted on Drive → delete locally
 *    - If not tracked or no driveId: new local file → upload to Drive
 * 
 * 3. DRIVE ONLY (exists on drive, not locally):
 *    - If tracked in DB: was synced before → deleted locally → delete from Drive
 *    - If not tracked: new remote file → download to local
 * 
 * 4. TRACKED BUT GONE FROM BOTH:
 *    - Remove from tracking DB
 *    
 * GOOGLE DOCS HANDLING:
 * Google Docs/Sheets/Slides are exported to Office formats locally:
 * - "document.gdoc" → "document.docx"
 * - "spreadsheet" → "spreadsheet.xlsx"
 * - "presentation" → "presentation.pptx"
 * We normalize paths to match Drive files with their local equivalents.
 */
class SyncDiffer @Inject constructor() {
    
    companion object {
        // Map of Google apps MIME types to their export extensions
        private val GOOGLE_DOCS_EXTENSIONS = mapOf(
            "application/vnd.google-apps.document" to ".docx",
            "application/vnd.google-apps.spreadsheet" to ".xlsx",
            "application/vnd.google-apps.presentation" to ".pptx",
            "application/vnd.google-apps.drawing" to ".png"
        )
        
        /**
         * Get the local file path for a Drive file.
         * For Google Docs files, this adds the appropriate extension.
         */
        fun getLocalPathForDriveFile(driveFile: DriveFile): String {
            val extension = GOOGLE_DOCS_EXTENSIONS[driveFile.mimeType]
            return if (extension != null && !driveFile.relativePath.endsWith(extension, ignoreCase = true)) {
                driveFile.relativePath + extension
            } else {
                driveFile.relativePath
            }
        }
        
        /**
         * Check if this is a Google Docs/Sheets/Slides file that needs export.
         */
        fun isGoogleDocsFile(mimeType: String?): Boolean {
            return mimeType?.startsWith("application/vnd.google-apps.") == true
        }
    }

    /**
     * Item to upload - includes the local file and optional existing Drive file ID.
     * If existingDriveFileId is set, we should UPDATE the existing file, not CREATE a new one.
     */
    data class UploadItem(
        val localFile: LocalFile,
        val existingDriveFileId: String? = null  // If set, update instead of create
    )

    data class SyncPlan(
        // Files/folders to upload (new or modified) - use UploadItem to distinguish create vs update
        val toUpload: List<UploadItem> = emptyList(),
        // Files/folders to download (new or modified)
        val toDownload: List<DriveFile> = emptyList(),
        // Files/folders to delete locally (deleted on Drive)
        val toDeleteLocal: List<SyncFileEntity> = emptyList(),
        // Files/folders to delete from Drive (deleted locally)
        val toDeleteDrive: List<SyncFileEntity> = emptyList(),
        // Conflicts requiring user resolution
        val conflicts: List<ConflictItem> = emptyList(),
        // Files that are in sync (no action needed)
        val unchanged: List<SyncFileEntity> = emptyList(),
        // Folders to create on Drive
        val foldersToCreateOnDrive: List<LocalFile> = emptyList(),
        // Folders to create locally
        val foldersToCreateLocally: List<DriveFile> = emptyList()
    ) {
        val hasChanges: Boolean
            get() = toUpload.isNotEmpty() || toDownload.isNotEmpty() ||
                    toDeleteLocal.isNotEmpty() || toDeleteDrive.isNotEmpty() ||
                    conflicts.isNotEmpty() ||
                    foldersToCreateOnDrive.isNotEmpty() || foldersToCreateLocally.isNotEmpty()
        
        val totalActions: Int
            get() = toUpload.size + toDownload.size + toDeleteLocal.size + toDeleteDrive.size +
                    foldersToCreateOnDrive.size + foldersToCreateLocally.size
    }

    data class ConflictItem(
        val localFile: LocalFile,
        val driveFile: DriveFile,
        val trackedFile: SyncFileEntity?
    )

    /**
     * Create a sync plan by comparing local files, drive files, and tracked files in DB.
     */
    fun createSyncPlan(
        localFiles: List<LocalFile>,
        driveFiles: List<DriveFile>,
        trackedFiles: List<SyncFileEntity>
    ): SyncPlan {
        // Build lookup maps (by relative path, case-insensitive)
        // Local files: keyed by their actual path
        val localMap = localFiles
            .filter { it.relativePath.isNotBlank() }
            .associateBy { it.relativePath.lowercase() }
        
        // Drive files: keyed by their LOCAL EQUIVALENT path
        // For Google Docs, this means "document" → "document.docx"
        val driveMap = driveFiles
            .filter { it.relativePath.isNotBlank() }
            .associateBy { getLocalPathForDriveFile(it).lowercase() }
        
        // Also keep original drive map for reverse lookup
        val driveByOriginalPath = driveFiles
            .filter { it.relativePath.isNotBlank() }
            .associateBy { it.relativePath.lowercase() }
        
        val trackedMap = trackedFiles
            .filter { it.localPath.isNotBlank() }
            .associateBy { it.localPath.lowercase() }

        // Separate files and folders
        val localFileMap = localMap.filterValues { !it.isDirectory }
        val localFolderMap = localMap.filterValues { it.isDirectory }
        val driveFileMap = driveMap.filterValues { !it.isFolder }
        val driveFolderMap = driveMap.filterValues { it.isFolder }
        val trackedFileMap = trackedMap.filterValues { !it.isDirectory }
        val trackedFolderMap = trackedMap.filterValues { it.isDirectory }

        // Results
        val toUpload = mutableListOf<UploadItem>()
        val toDownload = mutableListOf<DriveFile>()
        val toDeleteLocal = mutableListOf<SyncFileEntity>()
        val toDeleteDrive = mutableListOf<SyncFileEntity>()
        val conflicts = mutableListOf<ConflictItem>()
        val unchanged = mutableListOf<SyncFileEntity>()
        val foldersToCreateOnDrive = mutableListOf<LocalFile>()
        val foldersToCreateLocally = mutableListOf<DriveFile>()

        // Process all unique paths
        val allFilePaths = (localFileMap.keys + driveFileMap.keys + trackedFileMap.keys).distinct()
        val allFolderPaths = (localFolderMap.keys + driveFolderMap.keys + trackedFolderMap.keys).distinct()

        // Process FILES
        for (path in allFilePaths) {
            val local = localFileMap[path]
            val drive = driveFileMap[path]
            val tracked = trackedFileMap[path]

            when {
                // CASE 1: Exists both locally and on Drive
                local != null && drive != null -> {
                    val comparison = compareFiles(local, drive, tracked)
                    when (comparison) {
                        FileState.IDENTICAL -> {
                            // In sync - update tracking
                            unchanged.add(createTrackedEntry(local, drive))
                        }
                        FileState.LOCAL_NEWER -> {
                            // File exists on Drive, UPDATE it (don't create duplicate)
                            android.util.Log.d("SyncDiffer", "Local newer, will UPDATE existing: $path (driveId=${drive.id})")
                            toUpload.add(UploadItem(local, existingDriveFileId = drive.id))
                        }
                        FileState.REMOTE_NEWER -> {
                            toDownload.add(drive)
                        }
                        FileState.CONFLICT -> {
                            conflicts.add(ConflictItem(local, drive, tracked))
                        }
                    }
                }

                // CASE 2: Exists only locally
                local != null && drive == null -> {
                    if (tracked != null && tracked.driveFileId != null) {
                        // Was synced before with driveId
                        if (tracked.syncStatus == SyncStatus.PENDING_UPLOAD || 
                            tracked.syncStatus == SyncStatus.UPLOADING ||
                            tracked.syncStatus == SyncStatus.ERROR) {
                            // Retry failed upload - UPDATE existing file on Drive
                            android.util.Log.d("SyncDiffer", "Retrying pending upload (update): $path")
                            toUpload.add(UploadItem(local, existingDriveFileId = tracked.driveFileId))
                        } else {
                            // Was synced before, now gone from Drive → delete locally
                            android.util.Log.d("SyncDiffer", "File deleted on Drive, will delete locally: $path")
                            toDeleteLocal.add(tracked)
                        }
                    } else if (tracked != null && tracked.syncStatus == SyncStatus.PENDING_UPLOAD) {
                        // Tracked but never got driveId - retry upload as NEW file
                        android.util.Log.d("SyncDiffer", "Retrying upload (no driveId yet, create new): $path")
                        toUpload.add(UploadItem(local))
                    } else {
                        // New local file → upload + add to DB
                        android.util.Log.d("SyncDiffer", "New local file, will upload (create new): $path")
                        toUpload.add(UploadItem(local))
                    }
                }

                // CASE 3: Exists only on Drive
                local == null && drive != null -> {
                    if (tracked != null) {
                        // Was synced before
                        if (tracked.syncStatus == SyncStatus.PENDING_DOWNLOAD || 
                            tracked.syncStatus == SyncStatus.DOWNLOADING ||
                            tracked.syncStatus == SyncStatus.ERROR) {
                            // Retry failed download
                            android.util.Log.d("SyncDiffer", "Retrying pending download: $path")
                            toDownload.add(drive)
                        } else {
                            // Was synced before, now gone locally → delete from Drive
                            android.util.Log.d("SyncDiffer", "File deleted locally, will delete from Drive: $path")
                            toDeleteDrive.add(tracked)
                        }
                    } else {
                        // New remote file → download + add to DB
                        android.util.Log.d("SyncDiffer", "New remote file, will download: $path")
                        toDownload.add(drive)
                    }
                }

                // CASE 4: Gone from both but still tracked
                local == null && drive == null && tracked != null -> {
                    // File deleted from both sides - just remove tracking
                    android.util.Log.d("SyncDiffer", "File gone from both sides, removing tracking: $path")
                    // Will be cleaned up by caller
                }
            }
        }

        // Process FOLDERS
        for (path in allFolderPaths) {
            val local = localFolderMap[path]
            val drive = driveFolderMap[path]
            val tracked = trackedFolderMap[path]

            when {
                // Exists both - no action needed for folders
                local != null && drive != null -> {
                    unchanged.add(createTrackedFolderEntry(local, drive))
                }

                // Only locally - check if was synced before
                local != null && drive == null -> {
                    if (tracked != null && tracked.driveFileId != null) {
                        // Was synced, deleted on Drive → delete locally
                        android.util.Log.d("SyncDiffer", "Folder deleted on Drive, will delete locally: $path")
                        toDeleteLocal.add(tracked)
                    } else {
                        // New local folder → create on Drive
                        foldersToCreateOnDrive.add(local)
                    }
                }

                // Only on Drive - check if was synced before
                local == null && drive != null -> {
                    if (tracked != null) {
                        // Was synced, deleted locally → delete from Drive
                        android.util.Log.d("SyncDiffer", "Folder deleted locally, will delete from Drive: $path")
                        toDeleteDrive.add(tracked)
                    } else {
                        // New remote folder → create locally
                        foldersToCreateLocally.add(drive)
                    }
                }
            }
        }

        return SyncPlan(
            toUpload = toUpload,
            toDownload = toDownload,
            toDeleteLocal = toDeleteLocal.sortedByDescending { it.localPath.count { c -> c == '/' } }, // Delete children first
            toDeleteDrive = toDeleteDrive.sortedByDescending { it.localPath.count { c -> c == '/' } }, // Delete children first
            conflicts = conflicts,
            unchanged = unchanged,
            foldersToCreateOnDrive = foldersToCreateOnDrive.sortedBy { it.relativePath.count { c -> c == '/' } }, // Create parents first
            foldersToCreateLocally = foldersToCreateLocally.sortedBy { it.relativePath.count { c -> c == '/' } } // Create parents first
        )
    }

    private fun compareFiles(local: LocalFile, drive: DriveFile, tracked: SyncFileEntity?): FileState {
        // Check checksums first (most reliable)
        val localChecksum = local.checksum
        val driveChecksum = drive.md5Checksum

        if (localChecksum != null && driveChecksum != null) {
            if (localChecksum == driveChecksum) {
                return FileState.IDENTICAL
            }
        }

        // Compare modification times
        val driveModified = drive.modifiedTime ?: 0
        val localModified = local.lastModified
        val timeDiff = localModified - driveModified

        // If we have tracking info, check what changed since last sync
        if (tracked != null && tracked.lastSyncTime != null) {
            val lastSync = tracked.lastSyncTime
            val localChangedSinceSync = localModified > lastSync
            val driveChangedSinceSync = driveModified > lastSync

            return when {
                localChangedSinceSync && driveChangedSinceSync -> FileState.CONFLICT
                localChangedSinceSync -> FileState.LOCAL_NEWER
                driveChangedSinceSync -> FileState.REMOTE_NEWER
                else -> FileState.IDENTICAL
            }
        }

        // No tracking - use simple time comparison with 2 second tolerance
        return when {
            kotlin.math.abs(timeDiff) < 2000 -> {
                if (localChecksum != null && driveChecksum != null && localChecksum != driveChecksum) {
                    FileState.CONFLICT
                } else {
                    FileState.IDENTICAL
                }
            }
            timeDiff > 0 -> FileState.LOCAL_NEWER
            else -> FileState.REMOTE_NEWER
        }
    }

    private fun createTrackedEntry(local: LocalFile, drive: DriveFile): SyncFileEntity {
        return SyncFileEntity(
            localPath = local.relativePath,
            driveFileId = drive.id,
            fileName = local.name,
            isDirectory = false,
            fileSize = local.size,
            localModifiedTime = local.lastModified,
            driveModifiedTime = drive.modifiedTime,
            localChecksum = local.checksum,
            driveChecksum = drive.md5Checksum,
            syncStatus = SyncStatus.SYNCED,
            lastSyncTime = System.currentTimeMillis(),
            mimeType = local.mimeType
        )
    }

    private fun createTrackedFolderEntry(local: LocalFile, drive: DriveFile): SyncFileEntity {
        return SyncFileEntity(
            localPath = local.relativePath,
            driveFileId = drive.id,
            fileName = local.name,
            isDirectory = true,
            fileSize = 0,
            localModifiedTime = local.lastModified,
            driveModifiedTime = drive.modifiedTime,
            syncStatus = SyncStatus.SYNCED,
            lastSyncTime = System.currentTimeMillis()
        )
    }

    private enum class FileState {
        IDENTICAL,
        LOCAL_NEWER,
        REMOTE_NEWER,
        CONFLICT
    }
}
