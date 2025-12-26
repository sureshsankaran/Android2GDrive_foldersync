package com.foldersync.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.foldersync.domain.model.SyncStatus

@Composable
fun SyncStatusCard(
    status: SyncStatus,
    isSyncing: Boolean,
    lastSyncTime: String,
    pendingConflicts: Int = 0,
    progress: Float = 0f,
    currentFile: String = "",
    onViewConflicts: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val statusColor = when {
        isSyncing -> MaterialTheme.colorScheme.primary
        status == SyncStatus.SYNCED -> MaterialTheme.colorScheme.primary
        status == SyncStatus.CONFLICT -> MaterialTheme.colorScheme.error
        status == SyncStatus.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }

    val statusIcon = when {
        isSyncing -> Icons.Rounded.Sync
        status == SyncStatus.SYNCED -> Icons.Rounded.CheckCircle
        status == SyncStatus.CONFLICT -> Icons.Rounded.Warning
        status == SyncStatus.ERROR -> Icons.Rounded.Error
        else -> Icons.Rounded.Sync
    }

    val statusText = when {
        isSyncing -> "Syncing..."
        status == SyncStatus.SYNCED -> "Up to date"
        status == SyncStatus.CONFLICT -> "Conflicts detected"
        status == SyncStatus.ERROR -> "Sync error"
        status == SyncStatus.LOCAL_MODIFIED -> "Local changes pending"
        status == SyncStatus.DRIVE_MODIFIED -> "Drive changes pending"
        status == SyncStatus.PENDING_UPLOAD -> "Pending upload"
        status == SyncStatus.PENDING_DOWNLOAD -> "Pending download"
        else -> "Ready to sync"
    }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isSyncing) 360f else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "rotation"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(if (isSyncing) rotationAngle else 0f)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium,
                        color = statusColor
                    )
                    Text(
                        text = "Last sync: $lastSyncTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isSyncing && progress > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth()
                )
                if (currentFile.isNotEmpty()) {
                    Text(
                        text = currentFile,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (pendingConflicts > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$pendingConflicts conflict${if (pendingConflicts > 1) "s" else ""} need resolution",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onViewConflicts) {
                        Text("Resolve")
                    }
                }
            }
        }
    }
}
