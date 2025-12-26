package com.foldersync.domain.sync

import android.net.Uri
import com.foldersync.data.local.FileSystemManager
import com.foldersync.data.local.LocalFile
import com.foldersync.data.local.PreferencesManager
import com.foldersync.data.local.entity.SyncFileEntity
import com.foldersync.data.remote.drive.DriveFileManager
import com.foldersync.data.repository.AuthRepository
import com.foldersync.data.repository.SyncRepository
import com.foldersync.domain.model.ConflictResolutionStrategy
import com.foldersync.domain.model.DriveFile
import com.foldersync.domain.model.SyncAction
import com.foldersync.domain.model.SyncStatus
import com.foldersync.util.NetworkMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clean sync engine using database-tracked sync state for proper bidirectional sync.
 * 
 * Key principle: The database tracks which files have been synced. This allows us to
 * correctly distinguish between:
 * - New files (not in DB) that should be uploaded/downloaded
 * - Deleted files (in DB but missing from source) that should be deleted from target
 */
@Singleton
class SyncEngineV2 @Inject constructor(
    private val fileSystemManager: FileSystemManager,
    private val driveFileManager: DriveFileManager,
    private val syncRepository: SyncRepository,
    private val authRepository: AuthRepository,
    private val preferencesManager: PreferencesManager,
    private val networkMonitor: NetworkMonitor,
    private val syncDiffer: SyncDiffer,
    private val conflictResolver: ConflictResolver,
    @com.foldersync.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()
    
    private val syncMutex = Mutex()
    private var isCancelled = false

    companion object {
        private const val TAG = "SyncEngineV2"
    }
    
    /**
     * Perform a full bidirectional sync
     */
    suspend fun sync(localFolderUri: Uri, driveFolderId: String): SyncResult {
        if (!syncMutex.tryLock()) {
            return SyncResult(success = false, message = "Sync already in progress")
        }
        
        isCancelled = false
        val startTime = System.currentTimeMillis()
        
        try {
            // Check prerequisites
            val prereqResult = checkPrerequisites()
            if (!prereqResult.success) return prereqResult
            
            // === PHASE 1: Scan ===
            updateProgress(SyncState.SCANNING, message = "Scanning local files...")
            val localFiles = scanLocalFolder(localFolderUri)
            android.util.Log.d(TAG, "Local files: ${localFiles.size}")
            localFiles.forEach { android.util.Log.d(TAG, "  Local: ${it.relativePath} (dir=${it.isDirectory})") }
            if (isCancelled) return cancelledResult()
            
            updateProgress(SyncState.SCANNING, message = "Scanning Drive folder...")
            val driveFiles = listDriveFolder(driveFolderId)
            android.util.Log.d(TAG, "Drive files: ${driveFiles.size}")
            driveFiles.forEach { android.util.Log.d(TAG, "  Drive: ${it.relativePath} (folder=${it.isFolder})") }
            if (isCancelled) return cancelledResult()
            
            // === PHASE 2: Get tracked files from DB ===
            updateProgress(SyncState.COMPARING, message = "Comparing with sync history...")
            val trackedFiles = syncRepository.getAllTrackedFiles()
            android.util.Log.d(TAG, "Tracked files: ${trackedFiles.size}")
            trackedFiles.forEach { android.util.Log.d(TAG, "  Tracked: ${it.localPath} (driveId=${it.driveFileId})") }
            
            // === PHASE 3: Create sync plan ===
            val plan = syncDiffer.createSyncPlan(localFiles, driveFiles, trackedFiles)
            android.util.Log.d(TAG, "Sync plan: upload=${plan.toUpload.size}, download=${plan.toDownload.size}, " +
                "deleteLocal=${plan.toDeleteLocal.size}, deleteDrive=${plan.toDeleteDrive.size}, " +
                "conflicts=${plan.conflicts.size}, unchanged=${plan.unchanged.size}")
            
            if (!plan.hasChanges) {
                updateProgress(SyncState.COMPLETED, message = "Already in sync")
                preferencesManager.setLastSyncTime(System.currentTimeMillis())
                return SyncResult(success = true, message = "Already in sync")
            }
            
            updateProgress(SyncState.SYNCING, message = "Syncing...", totalFiles = plan.totalActions)
            
            // === PHASE 4: Execute sync plan ===
            val errors = mutableListOf<SyncError>()
            var uploadedCount = 0
            var downloadedCount = 0
            var deletedCount = 0
            val conflicts = mutableListOf<ConflictInfo>()
            
            // 4.1: Create folders on Drive
            for (folder in plan.foldersToCreateOnDrive) {
                if (isCancelled) return cancelledResult()
                try {
                    val pathParts = folder.relativePath.split("/")
                    val folderId = driveFileManager.findOrCreateFolderPath(driveFolderId, pathParts)
                    android.util.Log.d(TAG, "Created Drive folder: ${folder.relativePath} -> $folderId")
                    
                    // Track the folder
                    syncRepository.saveFile(SyncFileEntity(
                        localPath = folder.relativePath,
                        driveFileId = folderId,
                        fileName = folder.name,
                        isDirectory = true,
                        localModifiedTime = folder.lastModified,
                        syncStatus = SyncStatus.SYNCED,
                        lastSyncTime = System.currentTimeMillis()
                    ))
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to create Drive folder: ${folder.relativePath}", e)
                    errors.add(createError(folder.path, folder.name, e))
                }
            }
            
            // 4.2: Create folders locally
            for (folder in plan.foldersToCreateLocally) {
                if (isCancelled) return cancelledResult()
                try {
                    fileSystemManager.findOrCreatePath(localFolderUri, folder.relativePath)
                    android.util.Log.d(TAG, "Created local folder: ${folder.relativePath}")
                    
                    // Track the folder
                    syncRepository.saveFile(SyncFileEntity(
                        localPath = folder.relativePath,
                        driveFileId = folder.id,
                        fileName = folder.name,
                        isDirectory = true,
                        localModifiedTime = System.currentTimeMillis(),
                        driveModifiedTime = folder.modifiedTime,
                        syncStatus = SyncStatus.SYNCED,
                        lastSyncTime = System.currentTimeMillis()
                    ))
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to create local folder: ${folder.relativePath}", e)
                    errors.add(createError(folder.relativePath, folder.name, e))
                }
            }
            
            // 4.3: Upload files (create new or update existing)
            for (uploadItem in plan.toUpload) {
                if (isCancelled) return cancelledResult()
                
                val file = uploadItem.localFile
                val existingDriveFileId = uploadItem.existingDriveFileId
                
                // Mark as pending upload before starting
                syncRepository.saveFile(SyncFileEntity(
                    localPath = file.relativePath,
                    driveFileId = existingDriveFileId,  // Keep existing ID if updating
                    fileName = file.name,
                    isDirectory = false,
                    fileSize = file.size,
                    localModifiedTime = file.lastModified,
                    localChecksum = file.checksum,
                    syncStatus = SyncStatus.PENDING_UPLOAD,
                    lastSyncTime = null,
                    mimeType = file.mimeType
                ))
                
                try {
                    val uploadedDriveFile = if (existingDriveFileId != null) {
                        // UPDATE existing file on Drive (don't create duplicate)
                        android.util.Log.d(TAG, "Updating existing file on Drive: ${file.relativePath} (id=$existingDriveFileId)")
                        updateFile(existingDriveFileId, file)
                    } else {
                        // CREATE new file on Drive
                        android.util.Log.d(TAG, "Creating new file on Drive: ${file.relativePath}")
                        uploadFile(file, driveFolderId)
                    }
                    uploadedCount++
                    updateProgress(processedFiles = uploadedCount + downloadedCount + deletedCount)
                    
                    // Update tracking with success
                    syncRepository.saveFile(SyncFileEntity(
                        localPath = file.relativePath,
                        driveFileId = uploadedDriveFile.id,
                        fileName = file.name,
                        isDirectory = false,
                        fileSize = file.size,
                        localModifiedTime = file.lastModified,
                        localChecksum = file.checksum,
                        driveModifiedTime = uploadedDriveFile.modifiedTime,
                        driveChecksum = uploadedDriveFile.md5Checksum,
                        syncStatus = SyncStatus.SYNCED,
                        lastSyncTime = System.currentTimeMillis(),
                        mimeType = file.mimeType
                    ))
                    
                    val actionType = if (existingDriveFileId != null) SyncAction.UPDATE else SyncAction.UPLOAD
                    logAction(actionType, file.path, file.name, true)
                    android.util.Log.d(TAG, "Uploaded: ${file.relativePath}")
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to upload: ${file.relativePath}", e)
                    // Mark as error for retry next time
                    syncRepository.updateFileStatus(file.relativePath, SyncStatus.ERROR)
                    errors.add(createError(file.path, file.name, e))
                    logAction(SyncAction.UPLOAD, file.path, file.name, false, e.message)
                }
            }
            
            // 4.4: Download files
            for (file in plan.toDownload) {
                if (isCancelled) return cancelledResult()
                
                // Get the actual local path (may have extension added for Google Docs)
                val actualLocalPath = SyncDiffer.getLocalPathForDriveFile(file)
                val (localFileName, _) = getLocalFileNameAndMimeType(file.name, file.mimeType)
                
                // Mark as pending download before starting - use actual local path
                syncRepository.saveFile(SyncFileEntity(
                    localPath = actualLocalPath,
                    driveFileId = file.id,
                    fileName = localFileName,
                    isDirectory = false,
                    fileSize = file.size ?: 0L,
                    localModifiedTime = 0L,
                    driveModifiedTime = file.modifiedTime,
                    driveChecksum = file.md5Checksum,
                    syncStatus = SyncStatus.PENDING_DOWNLOAD,
                    lastSyncTime = null,
                    mimeType = file.mimeType
                ))
                
                try {
                    downloadFile(file, localFolderUri)
                    downloadedCount++
                    updateProgress(processedFiles = uploadedCount + downloadedCount + deletedCount)
                    
                    // Update tracking with success - use actual local path
                    syncRepository.saveFile(SyncFileEntity(
                        localPath = actualLocalPath,
                        driveFileId = file.id,
                        fileName = localFileName,
                        isDirectory = false,
                        fileSize = file.size ?: 0L,
                        localModifiedTime = System.currentTimeMillis(),
                        driveModifiedTime = file.modifiedTime,
                        driveChecksum = file.md5Checksum,
                        syncStatus = SyncStatus.SYNCED,
                        lastSyncTime = System.currentTimeMillis(),
                        mimeType = file.mimeType
                    ))
                    
                    logAction(SyncAction.DOWNLOAD, actualLocalPath, localFileName, true)
                    android.util.Log.d(TAG, "Downloaded: ${file.relativePath} -> $actualLocalPath")
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to download: ${file.relativePath}", e)
                    // Mark as error for retry next time - use actual local path
                    syncRepository.updateFileStatus(actualLocalPath, SyncStatus.ERROR)
                    errors.add(createError(actualLocalPath, file.name, e))
                    logAction(SyncAction.DOWNLOAD, actualLocalPath, file.name, false, e.message)
                }
            }
            
            // 4.5: Delete local files (deleted on Drive)
            for (trackedFile in plan.toDeleteLocal) {
                if (isCancelled) return cancelledResult()
                try {
                    // Find the local file URI
                    val localUri = fileSystemManager.findFile(localFolderUri, trackedFile.localPath)
                    if (localUri != null) {
                        fileSystemManager.deleteFile(localUri)
                        android.util.Log.d(TAG, "Deleted local: ${trackedFile.localPath}")
                    }
                    
                    // Remove from tracking
                    syncRepository.deleteFileByLocalPath(trackedFile.localPath)
                    deletedCount++
                    updateProgress(processedFiles = uploadedCount + downloadedCount + deletedCount)
                    logAction(SyncAction.DELETE_LOCAL, trackedFile.localPath, trackedFile.fileName, true)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to delete local: ${trackedFile.localPath}", e)
                    errors.add(createError(trackedFile.localPath, trackedFile.fileName, e))
                }
            }
            
            // 4.6: Delete Drive files (deleted locally)
            for (trackedFile in plan.toDeleteDrive) {
                if (isCancelled) return cancelledResult()
                try {
                    if (trackedFile.driveFileId != null) {
                        driveFileManager.deleteFile(trackedFile.driveFileId, trashInstead = true)
                        android.util.Log.d(TAG, "Deleted from Drive (moved to trash): ${trackedFile.localPath}")
                    }
                    
                    // Remove from tracking
                    syncRepository.deleteFileByLocalPath(trackedFile.localPath)
                    deletedCount++
                    updateProgress(processedFiles = uploadedCount + downloadedCount + deletedCount)
                    logAction(SyncAction.DELETE_DRIVE, trackedFile.localPath, trackedFile.fileName, true)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to delete from Drive: ${trackedFile.localPath}", e)
                    errors.add(createError(trackedFile.localPath, trackedFile.fileName, e))
                }
            }
            
            // 4.7: Handle conflicts
            val conflictStrategy = preferencesManager.conflictResolutionStrategy.first()
            for (conflict in plan.conflicts) {
                if (isCancelled) return cancelledResult()
                
                if (conflictStrategy == ConflictResolutionStrategy.ASK_USER) {
                    conflicts.add(ConflictInfo(
                        localPath = conflict.localFile.path,
                        driveFileId = conflict.driveFile.id,
                        fileName = conflict.localFile.name,
                        localModifiedTime = conflict.localFile.lastModified,
                        driveModifiedTime = conflict.driveFile.modifiedTime ?: 0,
                        localSize = conflict.localFile.size,
                        driveSize = conflict.driveFile.size ?: 0,
                        localChecksum = conflict.localFile.checksum,
                        driveChecksum = conflict.driveFile.md5Checksum
                    ))
                    syncRepository.updateFileStatus(conflict.localFile.relativePath, SyncStatus.CONFLICT)
                } else {
                    try {
                        val resolution = conflictResolver.resolve(
                            conflict.localFile, conflict.driveFile, conflictStrategy
                        )
                        applyConflictResolution(resolution, localFolderUri, driveFolderId)
                        logAction(SyncAction.CONFLICT_RESOLVED, conflict.localFile.path, conflict.localFile.name, true)
                    } catch (e: Exception) {
                        errors.add(createError(conflict.localFile.path, conflict.localFile.name, e))
                    }
                }
            }
            
            // 4.8: Update unchanged files in tracking (refresh lastSyncTime)
            for (unchanged in plan.unchanged) {
                syncRepository.saveFile(unchanged)
            }
            
            // Update last sync time
            preferencesManager.setLastSyncTime(System.currentTimeMillis())
            
            updateProgress(SyncState.COMPLETED, 
                message = "Sync completed", 
                processedFiles = uploadedCount + downloadedCount + deletedCount)
            
            return SyncResult(
                success = errors.isEmpty() && conflicts.isEmpty(),
                filesUploaded = uploadedCount,
                filesDownloaded = downloadedCount,
                filesDeleted = deletedCount,
                conflicts = conflicts,
                errors = errors,
                durationMs = System.currentTimeMillis() - startTime,
                message = when {
                    conflicts.isNotEmpty() -> "${conflicts.size} conflicts need resolution"
                    errors.isNotEmpty() -> "${errors.size} errors occurred"
                    else -> "Synced: $uploadedCount uploaded, $downloadedCount downloaded, $deletedCount deleted"
                }
            )
            
        } catch (e: CancellationException) {
            updateProgress(SyncState.CANCELLED)
            throw e
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Sync failed", e)
            updateProgress(SyncState.ERROR, message = e.message)
            return SyncResult(
                success = false,
                durationMs = System.currentTimeMillis() - startTime,
                message = e.message ?: "Unknown error"
            )
        } finally {
            syncMutex.unlock()
        }
    }
    
    fun cancel() {
        isCancelled = true
    }
    
    private suspend fun checkPrerequisites(): SyncResult {
        if (!authRepository.isAuthenticated()) {
            return SyncResult(success = false, message = "Not authenticated")
        }
        if (!networkMonitor.isCurrentlyOnline()) {
            return SyncResult(success = false, message = "No network connection")
        }
        val wifiOnly = preferencesManager.wifiOnly.first()
        if (wifiOnly && !networkMonitor.isOnWifi()) {
            return SyncResult(success = false, message = "Wi-Fi only mode enabled, but not on Wi-Fi")
        }
        return SyncResult(success = true)
    }
    
    private suspend fun scanLocalFolder(uri: Uri): List<LocalFile> = withContext(ioDispatcher) {
        fileSystemManager.scanFolder(uri).toList()
    }
    
    private suspend fun listDriveFolder(folderId: String): List<DriveFile> = withContext(ioDispatcher) {
        val allFiles = mutableListOf<DriveFile>()
        driveFileManager.listFilesRecursive(folderId).collect { file ->
            allFiles.add(file)
        }
        allFiles
    }
    
    private suspend fun uploadFile(file: LocalFile, rootFolderId: String): DriveFile = withContext(ioDispatcher) {
        val pathParts = file.relativePath.split("/")
        val folderParts = pathParts.dropLast(1)

        val targetFolderId = if (folderParts.isNotEmpty()) {
            driveFileManager.findOrCreateFolderPath(rootFolderId, folderParts)
        } else {
            rootFolderId
        }

        driveFileManager.uploadFile(
            localUri = file.uri,
            folderId = targetFolderId
        ) { uploaded, total ->
            val progress = if (total > 0) uploaded.toFloat() / total else 0f
            updateProgress(message = "Uploading ${file.name}: ${(progress * 100).toInt()}%")
        }
    }
    
    /**
     * Update an existing file on Drive (instead of creating a new one which causes duplicates)
     */
    private suspend fun updateFile(driveFileId: String, file: LocalFile): DriveFile = withContext(ioDispatcher) {
        driveFileManager.updateFile(
            fileId = driveFileId,
            localUri = file.uri,
            newName = file.name
        ) { uploaded, total ->
            val progress = if (total > 0) uploaded.toFloat() / total else 0f
            updateProgress(message = "Updating ${file.name}: ${(progress * 100).toInt()}%")
        }
    }
    
    private suspend fun downloadFile(driveFile: DriveFile, localFolderUri: Uri) = withContext(ioDispatcher) {
        val pathParts = driveFile.relativePath.split("/")
        val folderPath = pathParts.dropLast(1).joinToString("/")

        val targetFolderUri = if (folderPath.isNotEmpty()) {
            fileSystemManager.findOrCreatePath(localFolderUri, folderPath)
                ?: throw IOException("Failed to create folder structure: $folderPath")
        } else {
            localFolderUri
        }

        // Handle Google Docs files - need different filename and mimeType for export
        val (fileName, mimeType) = getLocalFileNameAndMimeType(driveFile.name, driveFile.mimeType)

        val localUri = fileSystemManager.createFile(
            parentUri = targetFolderUri,
            fileName = fileName,
            mimeType = mimeType
        ) ?: throw IOException("Failed to create local file: $fileName")
        
        driveFileManager.downloadFile(
            fileId = driveFile.id,
            destinationUri = localUri
        )
    }
    
    /**
     * Convert Google Docs file names and MIME types to local equivalents.
     * Google Docs/Sheets/Slides don't have file extensions and need to be exported.
     */
    private fun getLocalFileNameAndMimeType(originalName: String, mimeType: String): Pair<String, String> {
        return when (mimeType) {
            "application/vnd.google-apps.document" -> {
                val name = if (originalName.endsWith(".docx")) originalName else "$originalName.docx"
                name to "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            }
            "application/vnd.google-apps.spreadsheet" -> {
                val name = if (originalName.endsWith(".xlsx")) originalName else "$originalName.xlsx"
                name to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            }
            "application/vnd.google-apps.presentation" -> {
                val name = if (originalName.endsWith(".pptx")) originalName else "$originalName.pptx"
                name to "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            }
            "application/vnd.google-apps.drawing" -> {
                val name = if (originalName.endsWith(".png")) originalName else "$originalName.png"
                name to "image/png"
            }
            else -> originalName to mimeType
        }
    }
    
    private suspend fun applyConflictResolution(
        resolution: ConflictResolution,
        localFolderUri: Uri,
        driveFolderId: String
    ) = withContext(ioDispatcher) {
        when (resolution.action) {
            ConflictAction.UPLOAD_LOCAL -> {
                driveFileManager.updateFile(
                    fileId = resolution.driveFile.id,
                    localUri = resolution.localFile.uri
                )
            }
            ConflictAction.DOWNLOAD_REMOTE -> {
                downloadFile(resolution.driveFile, localFolderUri)
            }
            ConflictAction.KEEP_BOTH -> {
                driveFileManager.uploadFile(
                    localUri = resolution.localFile.uri,
                    folderId = driveFolderId
                )
            }
            else -> { /* Skip */ }
        }
    }
    
    private suspend fun logAction(
        action: SyncAction,
        path: String,
        name: String,
        success: Boolean,
        error: String? = null
    ) {
        syncRepository.logSyncAction(
            action = action,
            filePath = path,
            fileName = name,
            success = success,
            errorMessage = error,
            bytesTransferred = 0,
            durationMs = 0
        )
    }
    
    private fun updateProgress(
        state: SyncState = _syncProgress.value.state,
        message: String? = null,
        currentFile: String? = null,
        processedFiles: Int = _syncProgress.value.processedFiles,
        totalFiles: Int = _syncProgress.value.totalFiles
    ) {
        _syncProgress.value = _syncProgress.value.copy(
            state = state,
            message = message ?: _syncProgress.value.message,
            currentFile = currentFile ?: _syncProgress.value.currentFile,
            processedFiles = processedFiles,
            totalFiles = totalFiles
        )
    }
    
    private fun createError(path: String, name: String, e: Exception): SyncError {
        val errorType = when (e) {
            is IOException -> SyncErrorType.NETWORK_ERROR
            is SecurityException -> SyncErrorType.PERMISSION_DENIED
            else -> SyncErrorType.UNKNOWN
        }
        return SyncError(
            filePath = path,
            fileName = name,
            errorType = errorType,
            message = e.message ?: "Unknown error",
            cause = e
        )
    }
    
    private fun cancelledResult() = SyncResult(success = false, message = "Sync cancelled")
}
