package com.foldersync.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.foldersync.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for managing sync notifications
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID_SYNC_PROGRESS = "sync_progress"
        const val CHANNEL_ID_SYNC_RESULT = "sync_result"
        
        const val NOTIFICATION_ID_PROGRESS = 1001
        const val NOTIFICATION_ID_COMPLETE = 1002
        const val NOTIFICATION_ID_ERROR = 1003
    }

    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val progressChannel = NotificationChannel(
                CHANNEL_ID_SYNC_PROGRESS,
                "Sync Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows sync progress"
                setShowBadge(false)
            }

            val resultChannel = NotificationChannel(
                CHANNEL_ID_SYNC_RESULT,
                "Sync Results",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when sync completes or fails"
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannels(listOf(progressChannel, resultChannel))
        }
    }

    /**
     * Create sync progress notification for foreground service
     */
    fun createSyncProgressNotification(
        processedFiles: Int,
        totalFiles: Int,
        currentFile: String
    ): Notification {
        val progress = if (totalFiles > 0) (processedFiles * 100 / totalFiles) else 0
        
        return NotificationCompat.Builder(context, CHANNEL_ID_SYNC_PROGRESS)
            .setContentTitle("Syncing files...")
            .setContentText(currentFile.ifEmpty { "Preparing..." })
            .setSmallIcon(R.drawable.ic_sync)
            .setProgress(100, progress, totalFiles == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(createMainActivityIntent())
            .addAction(createCancelAction())
            .build()
    }

    /**
     * Update sync progress notification
     */
    fun updateSyncProgress(
        processedFiles: Int,
        totalFiles: Int,
        currentFile: String
    ) {
        val notification = createSyncProgressNotification(processedFiles, totalFiles, currentFile)
        try {
            notificationManager.notify(NOTIFICATION_ID_PROGRESS, notification)
        } catch (e: SecurityException) {
            // Permission not granted - ignore
        }
    }

    /**
     * Show sync complete notification
     */
    fun showSyncComplete(filesProcessed: Int) {
        dismissProgress()
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SYNC_RESULT)
            .setContentTitle("Sync Complete")
            .setContentText("Successfully synced $filesProcessed files")
            .setSmallIcon(R.drawable.ic_sync_done)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(createMainActivityIntent())
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_COMPLETE, notification)
        } catch (e: SecurityException) {
            // Permission not granted - ignore
        }
    }

    /**
     * Show sync error notification
     */
    fun showSyncError(errorMessage: String) {
        dismissProgress()
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SYNC_RESULT)
            .setContentTitle("Sync Failed")
            .setContentText(errorMessage)
            .setSmallIcon(R.drawable.ic_sync_error)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(createMainActivityIntent())
            .addAction(createRetryAction())
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_ERROR, notification)
        } catch (e: SecurityException) {
            // Permission not granted - ignore
        }
    }

    /**
     * Show conflict notification
     */
    fun showConflictNotification(conflictCount: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SYNC_RESULT)
            .setContentTitle("Sync Conflicts")
            .setContentText("$conflictCount files have conflicts that need resolution")
            .setSmallIcon(R.drawable.ic_warning)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(createMainActivityIntent())
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_ERROR, notification)
        } catch (e: SecurityException) {
            // Permission not granted - ignore
        }
    }

    /**
     * Dismiss progress notification
     */
    fun dismissProgress() {
        notificationManager.cancel(NOTIFICATION_ID_PROGRESS)
    }

    /**
     * Dismiss all notifications
     */
    fun dismissAll() {
        notificationManager.cancelAll()
    }

    private fun createMainActivityIntent(): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent()
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createCancelAction(): NotificationCompat.Action {
        val intent = Intent(context, SyncActionReceiver::class.java).apply {
            action = SyncActionReceiver.ACTION_CANCEL_SYNC
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_cancel,
            "Cancel",
            pendingIntent
        ).build()
    }

    private fun createRetryAction(): NotificationCompat.Action {
        val intent = Intent(context, SyncActionReceiver::class.java).apply {
            action = SyncActionReceiver.ACTION_RETRY_SYNC
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_refresh,
            "Retry",
            pendingIntent
        ).build()
    }
}
