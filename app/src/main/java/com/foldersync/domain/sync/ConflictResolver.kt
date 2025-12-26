package com.foldersync.domain.sync

import com.foldersync.data.local.LocalFile
import com.foldersync.domain.model.ConflictResolutionStrategy
import com.foldersync.domain.model.DriveFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Resolves file conflicts based on the configured strategy
 */
class ConflictResolver @Inject constructor() {

    /**
     * Resolve a single conflict
     */
    fun resolve(
        local: LocalFile,
        drive: DriveFile,
        strategy: ConflictResolutionStrategy
    ): ConflictResolution {
        return when (strategy) {
            ConflictResolutionStrategy.KEEP_LOCAL -> {
                ConflictResolution(
                    action = ConflictAction.UPLOAD_LOCAL,
                    localFile = local,
                    driveFile = drive,
                    newFileName = null
                )
            }
            
            ConflictResolutionStrategy.KEEP_REMOTE -> {
                ConflictResolution(
                    action = ConflictAction.DOWNLOAD_REMOTE,
                    localFile = local,
                    driveFile = drive,
                    newFileName = null
                )
            }
            
            ConflictResolutionStrategy.KEEP_NEWEST -> {
                val driveModified = drive.modifiedTime ?: 0
                if (local.lastModified >= driveModified) {
                    ConflictResolution(
                        action = ConflictAction.UPLOAD_LOCAL,
                        localFile = local,
                        driveFile = drive,
                        newFileName = null
                    )
                } else {
                    ConflictResolution(
                        action = ConflictAction.DOWNLOAD_REMOTE,
                        localFile = local,
                        driveFile = drive,
                        newFileName = null
                    )
                }
            }
            
            ConflictResolutionStrategy.KEEP_BOTH -> {
                val conflictSuffix = generateConflictSuffix()
                val newName = generateConflictFileName(local.name, conflictSuffix)
                ConflictResolution(
                    action = ConflictAction.KEEP_BOTH,
                    localFile = local,
                    driveFile = drive,
                    newFileName = newName
                )
            }
            
            ConflictResolutionStrategy.ASK_USER -> {
                ConflictResolution(
                    action = ConflictAction.PENDING_USER_INPUT,
                    localFile = local,
                    driveFile = drive,
                    newFileName = null
                )
            }
        }
    }
    
    /**
     * Resolve multiple conflicts at once
     */
    fun resolveBatch(
        conflicts: List<Pair<LocalFile, DriveFile>>,
        strategy: ConflictResolutionStrategy
    ): List<ConflictResolution> {
        return conflicts.map { (local, drive) -> resolve(local, drive, strategy) }
    }
    
    /**
     * Generate a unique suffix for conflict files
     */
    private fun generateConflictSuffix(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return "conflict_${dateFormat.format(Date())}"
    }
    
    /**
     * Generate a new filename for keeping both versions
     * Example: "document.pdf" -> "document_conflict_20231225_143052.pdf"
     */
    private fun generateConflictFileName(originalName: String, suffix: String): String {
        val lastDotIndex = originalName.lastIndexOf('.')
        return if (lastDotIndex > 0) {
            val nameWithoutExt = originalName.substring(0, lastDotIndex)
            val extension = originalName.substring(lastDotIndex)
            "${nameWithoutExt}_$suffix$extension"
        } else {
            "${originalName}_$suffix"
        }
    }
}

enum class ConflictAction {
    UPLOAD_LOCAL,
    DOWNLOAD_REMOTE,
    KEEP_BOTH,
    PENDING_USER_INPUT,
    SKIP
}

data class ConflictResolution(
    val action: ConflictAction,
    val localFile: LocalFile,
    val driveFile: DriveFile,
    val newFileName: String?
)
