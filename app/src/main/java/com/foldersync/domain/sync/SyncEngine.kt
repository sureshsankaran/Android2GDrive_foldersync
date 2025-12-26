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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncEngine @Inject constructor(
    private val fileSystemManager: FileSystemManager,
    private val driveFileManager: DriveFileManager,
    private val syncRepository: SyncRepository,
    private val authRepository: AuthRepository,
    private val preferencesManager: PreferencesManager,
    private val networkMonitor: NetworkMonitor,
    private val fileDiffer: FileDiffer,
    private val conflictResolver: ConflictResolver,
    @com.foldersync.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()
    
    private val syncMutex = Mutex()
    private var isCancelled = false
    
    /**
     * Perform a full bidirectional sync
     */
    suspend fun sync(localFolderUri: Uri, driveFolderId: String): SyncResult {
        // Prevent concurrent syncs
        if (!syncMutex.tryLock()) {
            return SyncResult(
                success = false,
                message = "Sync already in progress"
            )
        }
        
        isCancelled = false
        val startTime = System.currentTimeMillis()
        
        try {
            // Check prerequisites
            val prereqResult = checkPrerequisites()
            if (!prereqResult.success) {
                return prereqResult
            }
            
            updateProgress(SyncState.SCANNING, message = "Scanning local files...")
            
            // 1. Scan local folder
            val localFiles = scanLocalFolder(localFolderUri)
            if (isCancelled) return cancelledResult()
            
            updateProgress(
                SyncState.SCANNING,
                message = "Scanning Drive folder...",
                totalFiles = localFiles.size
            )
            
            // 2. List Drive folder
            val driveFiles = listDriveFolder(driveFolderId)
            if (isCancelled) return cancelledResult()
            
            updateProgress(
                SyncState.COMPARING,
                message = "Comparing files...",
                totalFiles = localFiles.size + driveFiles.size
            )
            
            // 3. Get last sync time
            val lastSyncTime = preferencesManager.lastSyncTime.first()
            
            // 4. Diff files
            val diffResult = fileDiffer.diff(localFiles, driveFiles, lastSyncTime)
            if (isCancelled) return cancelledResult()
            
            updateProgress(
                SyncState.SYNCING,
                message = "Syncing files...",
                totalFiles = diffResult.totalChanges
            )
            
            // 5. Process sync actions
            val errors = mutableListOf<SyncError>()
            var uploadedCount = 0
            var downloadedCount = 0
            var deletedCount = 0
            val conflicts = mutableListOf<ConflictInfo>()
            
            // Upload new local files
            for (file in diffResult.newLocal) {
                if (isCancelled) return cancelledResult()
                try {
                    uploadFile(file, driveFolderId)
                    uploadedCount++
                    updateProgress(processedFiles = uploadedCount + downloadedCount)
                    logAction(SyncAction.UPLOAD, file.path, file.name, true)
                } catch (e: Exception) {
                    errors.add(createError(file.path, file.name, e))
                    logAction(SyncAction.UPLOAD, file.path, file.name, false, e.message)
                }
            }
            
            // Download new remote files
            for (file in diffResult.newRemote) {
                if (isCancelled) return cancelledResult()
                try {
                    downloadFile(file, localFolderUri)
                    downloadedCount++
                    updateProgress(processedFiles = uploadedCount + downloadedCount)
                    logAction(SyncAction.DOWNLOAD, file.name, file.name, true)
                } catch (e: Exception) {
                    errors.add(createError(file.name, file.name, e))
                    logAction(SyncAction.DOWNLOAD, file.name, file.name, false, e.message)
                }
            }
            
            // Upload modified local files
            for ((local, drive) in diffResult.modifiedLocal) {
                if (isCancelled) return cancelledResult()
                try {
                    updateDriveFile(local, drive.id)
                    uploadedCount++
                    updateProgress(processedFiles = uploadedCount + downloadedCount)
                    logAction(SyncAction.UPLOAD, local.path, local.name, true)
                } catch (e: Exception) {
                    errors.add(createError(local.path, local.name, e))
                }
            }
            
            // Download modified remote files
            for ((local, drive) in diffResult.modifiedRemote) {
                if (isCancelled) return cancelledResult()
                try {
                    downloadFile(drive, localFolderUri)
                    downloadedCount++
                    updateProgress(processedFiles = uploadedCount + downloadedCount)
                    logAction(SyncAction.DOWNLOAD, local.path, local.name, true)
                } catch (e: Exception) {
                    errors.add(createError(local.path, local.name, e))
                }
            }
            
            // Handle conflicts
            val conflictStrategy = preferencesManager.conflictResolutionStrategy.first()
            for ((local, drive) in diffResult.conflicts) {
                if (isCancelled) return cancelledResult()
                
                if (conflictStrategy == ConflictResolutionStrategy.ASK_USER) {
                    // Add to conflicts list for user resolution
                    conflicts.add(
                        ConflictInfo(
                            localPath = local.path,
                            driveFileId = drive.id,
                            fileName = local.name,
                            localModifiedTime = local.lastModified,
                            driveModifiedTime = drive.modifiedTime ?: 0,
                            localSize = local.size,
                            driveSize = drive.size ?: 0,
                            localChecksum = local.checksum,
                            driveChecksum = drive.md5Checksum
                        )
                    )
                    
                    // Update database with conflict status
                    syncRepository.updateFileStatus(local.path, SyncStatus.CONFLICT)
                } else {
                    // Auto-resolve conflict
                    try {
                        val resolution = conflictResolver.resolve(local, drive, conflictStrategy)
                        applyConflictResolution(resolution, localFolderUri, driveFolderId)
                        logAction(SyncAction.CONFLICT_RESOLVED, local.path, local.name, true)
                    } catch (e: Exception) {
                        errors.add(createError(local.path, local.name, e))
                    }
                }
            }
            
            // Update last sync time
            preferencesManager.setLastSyncTime(System.currentTimeMillis())
            
            updateProgress(
                SyncState.COMPLETED,
                message = "Sync completed",
                processedFiles = uploadedCount + downloadedCount + deletedCount
            )
            
            return SyncResult(
                success = errors.isEmpty() && conflicts.isEmpty(),
                filesUploaded = uploadedCount,
                filesDownloaded = downloadedCount,
                filesDeleted = deletedCount,
                conflicts = conflicts,
                errors = errors,
                durationMs = System.currentTimeMillis() - startTime,
                message = if (conflicts.isNotEmpty()) "${conflicts.size} conflicts need resolution" else "Sync completed"
            )
            
        } catch (e: CancellationException) {
            updateProgress(SyncState.CANCELLED)
            throw e
        } catch (e: Exception) {
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
    
    /**
     * Cancel ongoing sync
     */
    fun cancel() {
        isCancelled = true
    }
    
    private suspend fun checkPrerequisites(): SyncResult {
        // Check authentication
        if (!authRepository.isAuthenticated()) {
            return SyncResult(success = false, message = "Not authenticated")
        }
        
        // Check network
        if (!networkMonitor.isCurrentlyOnline()) {
            return SyncResult(success = false, message = "No network connection")
        }
        
        // Check Wi-Fi only setting
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
    
    private suspend fun uploadFile(file: LocalFile, rootFolderId: String) = withContext(ioDispatcher) {
        // Parse relative path to get parent folder parts
        val pathParts = file.relativePath.split("/")
        val folderParts = pathParts.dropLast(1) // All parts except the filename

        // Create folder structure on Drive if needed
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
    
    private suspend fun downloadFile(driveFile: DriveFile, localFolderUri: Uri) = withContext(ioDispatcher) {
        // Parse relative path to get parent folder parts
        val pathParts = driveFile.relativePath.split("/")
        val folderParts = pathParts.dropLast(1) // All parts except the filename

        // Create local folder structure if needed
        val targetFolderUri = if (folderParts.isNotEmpty()) {
            fileSystemManager.findOrCreatePath(localFolderUri, folderParts)
        } else {
            localFolderUri
        }

        // Create local file
        val localUri = fileSystemManager.createFile(
            parentUri = targetFolderUri,
            fileName = driveFile.name,
            mimeType = driveFile.mimeType
        ) ?: throw IOException("Failed to create local file: ${driveFile.name}")
        
        // Download content
        driveFileManager.downloadFile(
            fileId = driveFile.id,
            destinationUri = localUri
        )
    }
    
    private suspend fun updateDriveFile(local: LocalFile, driveFileId: String) = withContext(ioDispatcher) {
        driveFileManager.updateFile(
            fileId = driveFileId,
            localUri = local.uri
        )
    }
    
    private suspend fun applyConflictResolution(
        resolution: ConflictResolution,
        localFolderUri: Uri,
        driveFolderId: String
    ) = withContext(ioDispatcher) {
        when (resolution.action) {
            ConflictAction.UPLOAD_LOCAL -> {
                updateDriveFile(resolution.localFile, resolution.driveFile.id)
            }
            ConflictAction.DOWNLOAD_REMOTE -> {
                downloadFile(resolution.driveFile, localFolderUri)
            }
            ConflictAction.KEEP_BOTH -> {
                // Upload local with new name
                // Note: The file will be uploaded with its original name from the URI,
                // renaming would require additional API call after upload
                driveFileManager.uploadFile(
                    localUri = resolution.localFile.uri,
                    folderId = driveFolderId
                )
            }
            else -> { /* Skip or pending */ }
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
    
    private fun cancelledResult() = SyncResult(
        success = false,
        message = "Sync cancelled"
    )
}
