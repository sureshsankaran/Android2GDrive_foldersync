package com.foldersync.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foldersync.domain.model.SyncStatus
import com.foldersync.ui.components.DriveFolderCard
import com.foldersync.ui.components.LocalFolderCard
import com.foldersync.ui.components.SyncStatusCard

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    onSelectFolders: () -> Unit,
    onDriveFolderSelect: () -> Unit,
    onOpenConflicts: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "FolderSync") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        HomeContent(
            paddingValues = paddingValues,
            uiState = uiState,
            onSyncNow = viewModel::triggerSync,
            onSelectFolders = onSelectFolders,
            onSelectDriveFolder = onDriveFolderSelect,
            onOpenConflicts = onOpenConflicts
        )
    }
}

@Composable
private fun HomeContent(
    paddingValues: PaddingValues,
    uiState: HomeUiState,
    onSyncNow: () -> Unit,
    onSelectFolders: () -> Unit,
    onSelectDriveFolder: () -> Unit,
    onOpenConflicts: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        SyncStatusCard(
            status = uiState.syncStatus,
            isSyncing = uiState.isSyncing,
            lastSyncTime = uiState.lastSyncTime,
            pendingConflicts = uiState.pendingConflicts,
            onViewConflicts = onOpenConflicts
        )

        Text(
            text = "Folders",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )

        LocalFolderCard(
            folderUri = uiState.localFolderUri,
            folderName = uiState.localFolderUri?.lastPathSegment,
            onClick = onSelectFolders
        )

        DriveFolderCard(
            folderName = uiState.driveFolderName,
            onClick = onSelectDriveFolder
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                enabled = !uiState.isSyncing && uiState.localFolderUri != null && uiState.driveFolderName != null,
                onClick = onSyncNow
            ) {
                Text(text = if (uiState.isSyncing) "Syncing..." else "Sync Now")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun Uri.toReadablePath(): String {
    return lastPathSegment ?: toString()
}
