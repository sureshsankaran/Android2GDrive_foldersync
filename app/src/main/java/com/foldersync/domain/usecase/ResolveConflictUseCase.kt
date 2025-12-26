package com.foldersync.domain.usecase

import com.foldersync.domain.sync.ConflictInfo
import com.foldersync.domain.sync.ConflictResolver
import com.foldersync.domain.sync.ConflictResolution
import com.foldersync.domain.model.ConflictResolutionStrategy
import com.foldersync.data.local.LocalFile
import com.foldersync.domain.model.DriveFile
import javax.inject.Inject

/**
 * Use case for resolving individual sync conflicts
 */
class ResolveConflictUseCase @Inject constructor(
    private val conflictResolver: ConflictResolver
) {
    /**
     * Resolve a single conflict with a specific strategy
     */
    operator fun invoke(
        conflict: ConflictInfo,
        strategy: ConflictResolutionStrategy
    ): ConflictResolution {
        val localFile = LocalFile(
            uri = android.net.Uri.parse(conflict.localPath),
            name = conflict.fileName,
            path = conflict.localPath,
            size = conflict.localSize,
            mimeType = "application/octet-stream",
            isDirectory = false,
            lastModified = conflict.localModifiedTime,
            checksum = conflict.localChecksum
        )
        val driveFile = DriveFile(
            id = conflict.driveFileId ?: "",
            name = conflict.fileName,
            mimeType = "application/octet-stream",
            modifiedTime = conflict.driveModifiedTime,
            size = conflict.driveSize,
            md5Checksum = conflict.driveChecksum,
            parents = null,
            version = null
        )
        return conflictResolver.resolve(localFile, driveFile, strategy)
    }

    /**
     * Resolve multiple conflicts with the same strategy
     */
    fun resolveAll(
        conflicts: List<ConflictInfo>,
        strategy: ConflictResolutionStrategy
    ): List<ConflictResolution> {
        return conflicts.map { conflict ->
            invoke(conflict, strategy)
        }
    }

    /**
     * Resolve conflicts with per-conflict strategies
     */
    fun resolveWithStrategies(
        conflictsWithStrategies: List<Pair<ConflictInfo, ConflictResolutionStrategy>>
    ): List<ConflictResolution> {
        return conflictsWithStrategies.map { (conflict, strategy) ->
            invoke(conflict, strategy)
        }
    }
}
