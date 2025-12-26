package com.foldersync.domain.sync

import com.foldersync.data.local.LocalFile
import com.foldersync.domain.model.DriveFile
import javax.inject.Inject

/**
 * Compares local files with Drive files to determine sync actions
 */
class FileDiffer @Inject constructor() {

    /**
     * Compare local and remote files to determine what needs to be synced
     */
    fun diff(
        localFiles: List<LocalFile>,
        driveFiles: List<DriveFile>,
        lastSyncTime: Long?
    ): DiffResult {
        val localMap = buildLocalFileMap(localFiles)
        val driveMap = buildDriveFileMap(driveFiles)
        
        android.util.Log.d("FileDiffer", "LocalMap keys: ${localMap.keys.take(20)}")
        android.util.Log.d("FileDiffer", "DriveMap keys: ${driveMap.keys.take(20)}")
        
        // Also build folder maps for folder sync
        val localFolderMap = buildLocalFolderMap(localFiles)
        val driveFolderMap = buildDriveFolderMap(driveFiles)
        
        val allKeys = localMap.keys + driveMap.keys
        
        val newLocal = mutableListOf<LocalFile>()
        val newRemote = mutableListOf<DriveFile>()
        val modifiedLocal = mutableListOf<Pair<LocalFile, DriveFile>>()
        val modifiedRemote = mutableListOf<Pair<LocalFile, DriveFile>>()
        val conflicts = mutableListOf<Pair<LocalFile, DriveFile>>()
        val unchanged = mutableListOf<Pair<LocalFile, DriveFile>>()
        val deletedLocal = mutableListOf<DriveFile>()
        val deletedRemote = mutableListOf<LocalFile>()
        
        // Determine new folders to create and folders to delete
        val newLocalFolders = mutableListOf<LocalFile>()
        val newRemoteFolders = mutableListOf<DriveFile>()
        val deletedLocalFolders = mutableListOf<DriveFile>()  // Folders deleted locally, delete from Drive
        val deletedRemoteFolders = mutableListOf<LocalFile>() // Folders deleted on Drive, delete locally
        
        // Folders that exist locally but not on Drive - create on Drive
        for ((path, folder) in localFolderMap) {
            if (!driveFolderMap.containsKey(path)) {
                // Always create folder on Drive (safer than deletion)
                newLocalFolders.add(folder)
            }
        }
        
        // Folders that exist on Drive but not locally - create locally
        for ((path, folder) in driveFolderMap) {
            if (!localFolderMap.containsKey(path)) {
                // Always create folder locally (safer than deletion)
                newRemoteFolders.add(folder)
            }
        }
        
        for (key in allKeys) {
            val local = localMap[key]
            val drive = driveMap[key]
            
            when {
                // File exists only locally - upload to Drive
                local != null && drive == null -> {
                    // Always upload (safer than assuming it was deleted remotely)
                    android.util.Log.d("FileDiffer", "File only local, will upload: ${local.relativePath}")
                    newLocal.add(local)
                }
                
                // File exists only on Drive - download to local
                local == null && drive != null -> {
                    // Always download (safer than assuming it was deleted locally)
                    android.util.Log.d("FileDiffer", "File only on Drive, will download: ${drive.relativePath}")
                    newRemote.add(drive)
                }
                
                // File exists in both places
                local != null && drive != null -> {
                    val comparison = compareFiles(local, drive)
                    when (comparison) {
                        FileComparison.IDENTICAL -> unchanged.add(local to drive)
                        FileComparison.LOCAL_NEWER -> modifiedLocal.add(local to drive)
                        FileComparison.REMOTE_NEWER -> modifiedRemote.add(local to drive)
                        FileComparison.CONFLICT -> conflicts.add(local to drive)
                    }
                }
            }
        }
        
        return DiffResult(
            newLocal = newLocal,
            newRemote = newRemote,
            modifiedLocal = modifiedLocal,
            modifiedRemote = modifiedRemote,
            conflicts = conflicts,
            unchanged = unchanged,
            deletedLocal = deletedLocal,
            deletedRemote = deletedRemote,
            newLocalFolders = newLocalFolders,
            newRemoteFolders = newRemoteFolders,
            deletedLocalFolders = deletedLocalFolders,
            deletedRemoteFolders = deletedRemoteFolders
        )
    }
    
    private fun buildLocalFileMap(files: List<LocalFile>): Map<String, LocalFile> {
        return files
            .filter { !it.isDirectory && it.relativePath.isNotBlank() }
            .associateBy { it.relativePath.lowercase() }
    }
    
    private fun buildDriveFileMap(files: List<DriveFile>): Map<String, DriveFile> {
        return files
            .filter { !it.isFolder && it.relativePath.isNotBlank() }
            .associateBy { it.relativePath.lowercase() }
    }
    
    private fun buildLocalFolderMap(files: List<LocalFile>): Map<String, LocalFile> {
        return files
            .filter { it.isDirectory && it.relativePath.isNotBlank() }
            .associateBy { it.relativePath.lowercase() }
    }
    
    private fun buildDriveFolderMap(files: List<DriveFile>): Map<String, DriveFile> {
        return files
            .filter { it.isFolder && it.relativePath.isNotBlank() }
            .associateBy { it.relativePath.lowercase() }
    }
    
    private fun compareFiles(local: LocalFile, drive: DriveFile): FileComparison {
        // First check checksums if available
        val localChecksum = local.checksum
        val driveChecksum = drive.md5Checksum
        
        if (localChecksum != null && driveChecksum != null) {
            if (localChecksum == driveChecksum) {
                return FileComparison.IDENTICAL
            }
        }
        
        // If checksums differ or unavailable, compare by modification time
        val driveModified = drive.modifiedTime ?: 0
        val localModified = local.lastModified
        
        // Allow 2 second tolerance for time comparison
        val timeDiff = localModified - driveModified
        
        return when {
            kotlin.math.abs(timeDiff) < 2000 -> {
                // Times are close, check if checksums differ
                if (localChecksum != null && driveChecksum != null && localChecksum != driveChecksum) {
                    FileComparison.CONFLICT
                } else {
                    FileComparison.IDENTICAL
                }
            }
            timeDiff > 0 -> FileComparison.LOCAL_NEWER
            else -> FileComparison.REMOTE_NEWER
        }
    }
}

enum class FileComparison {
    IDENTICAL,
    LOCAL_NEWER,
    REMOTE_NEWER,
    CONFLICT
}

/**
 * Result of file comparison between local and Drive
 */
data class DiffResult(
    val newLocal: List<LocalFile>,
    val newRemote: List<DriveFile>,
    val modifiedLocal: List<Pair<LocalFile, DriveFile>>,
    val modifiedRemote: List<Pair<LocalFile, DriveFile>>,
    val conflicts: List<Pair<LocalFile, DriveFile>>,
    val unchanged: List<Pair<LocalFile, DriveFile>>,
    val deletedLocal: List<DriveFile>,
    val deletedRemote: List<LocalFile>,
    val newLocalFolders: List<LocalFile> = emptyList(),
    val newRemoteFolders: List<DriveFile> = emptyList(),
    val deletedLocalFolders: List<DriveFile> = emptyList(),  // Folders deleted locally, to delete from Drive
    val deletedRemoteFolders: List<LocalFile> = emptyList()  // Folders deleted on Drive, to delete locally
) {
    val hasChanges: Boolean
        get() = newLocal.isNotEmpty() || newRemote.isNotEmpty() ||
                modifiedLocal.isNotEmpty() || modifiedRemote.isNotEmpty() ||
                conflicts.isNotEmpty() || deletedLocal.isNotEmpty() || deletedRemote.isNotEmpty() ||
                newLocalFolders.isNotEmpty() || newRemoteFolders.isNotEmpty() ||
                deletedLocalFolders.isNotEmpty() || deletedRemoteFolders.isNotEmpty()
    
    val totalChanges: Int
        get() = newLocal.size + newRemote.size + modifiedLocal.size + 
                modifiedRemote.size + conflicts.size + deletedLocal.size + deletedRemote.size +
                newLocalFolders.size + newRemoteFolders.size +
                deletedLocalFolders.size + deletedRemoteFolders.size
}
