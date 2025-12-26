package com.foldersync.domain.usecase

import android.net.Uri
import com.foldersync.domain.sync.SyncEngine
import com.foldersync.domain.sync.SyncProgress
import com.foldersync.domain.sync.SyncResult
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Use case for executing folder synchronization
 */
class SyncFolderUseCase @Inject constructor(
    private val syncEngine: SyncEngine
) {
    /**
     * Get the sync progress state flow
     */
    val syncProgress: StateFlow<SyncProgress>
        get() = syncEngine.syncProgress

    /**
     * Execute sync with default conflict resolution (prefer remote)
     */
    suspend operator fun invoke(
        localFolderUri: Uri,
        driveFolderId: String
    ): SyncResult {
        return syncEngine.sync(
            localFolderUri = localFolderUri,
            driveFolderId = driveFolderId
        )
    }

    /**
     * Execute sync with string URI (convenience method)
     */
    suspend fun syncWithStringUri(
        localFolderUri: String,
        driveFolderId: String
    ): SyncResult {
        return syncEngine.sync(
            localFolderUri = Uri.parse(localFolderUri),
            driveFolderId = driveFolderId
        )
    }

    /**
     * Cancel ongoing sync operation
     */
    fun cancel() {
        syncEngine.cancel()
    }
}
