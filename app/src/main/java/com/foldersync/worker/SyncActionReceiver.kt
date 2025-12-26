package com.foldersync.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Broadcast receiver for sync notification actions
 */
@AndroidEntryPoint
class SyncActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CANCEL_SYNC = "com.foldersync.ACTION_CANCEL_SYNC"
        const val ACTION_RETRY_SYNC = "com.foldersync.ACTION_RETRY_SYNC"
    }

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_CANCEL_SYNC -> {
                syncScheduler.cancelManualSync()
                notificationHelper.dismissProgress()
            }
            ACTION_RETRY_SYNC -> {
                syncScheduler.triggerManualSync()
            }
        }
    }
}
