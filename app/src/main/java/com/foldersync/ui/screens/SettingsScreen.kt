package com.foldersync.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foldersync.domain.sync.ConflictResolutionStrategy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        SettingsContent(
            paddingValues = paddingValues,
            uiState = uiState,
            onAutoSyncChanged = viewModel::setAutoSync,
            onSyncIntervalChanged = viewModel::setSyncInterval,
            onWifiOnlyChanged = viewModel::setWifiOnly,
            onChargingOnlyChanged = viewModel::setChargingOnly,
            onConflictStrategyChanged = viewModel::setConflictStrategy
        )
    }
}

@Composable
private fun SettingsContent(
    paddingValues: PaddingValues,
    uiState: SettingsUiState,
    onAutoSyncChanged: (Boolean) -> Unit,
    onSyncIntervalChanged: (Int) -> Unit,
    onWifiOnlyChanged: (Boolean) -> Unit,
    onChargingOnlyChanged: (Boolean) -> Unit,
    onConflictStrategyChanged: (ConflictResolutionStrategy) -> Unit
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(scrollState)
    ) {
        SettingsSection(title = "Sync Settings") {
            SwitchSettingItem(
                title = "Auto Sync",
                subtitle = "Automatically sync files in background",
                checked = uiState.autoSyncEnabled,
                onCheckedChange = onAutoSyncChanged
            )

            if (uiState.autoSyncEnabled) {
                SyncIntervalSetting(
                    currentInterval = uiState.syncIntervalMinutes,
                    onIntervalSelected = onSyncIntervalChanged
                )
            }

            SwitchSettingItem(
                title = "WiFi Only",
                subtitle = "Only sync when connected to WiFi",
                checked = uiState.wifiOnly,
                onCheckedChange = onWifiOnlyChanged
            )

            SwitchSettingItem(
                title = "While Charging",
                subtitle = "Only sync while device is charging",
                checked = uiState.chargingOnly,
                onCheckedChange = onChargingOnlyChanged
            )
        }

        SettingsSection(title = "Conflict Resolution") {
            ConflictStrategySetting(
                currentStrategy = uiState.conflictStrategy,
                onStrategySelected = onConflictStrategyChanged
            )
        }

        SettingsSection(title = "About") {
            Text(
                text = "FolderSync v1.0.0",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SwitchSettingItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SyncIntervalSetting(
    currentInterval: Int,
    onIntervalSelected: (Int) -> Unit
) {
    val intervals = listOf(15, 30, 60, 120, 360, 720, 1440)
    val labels = listOf("15 min", "30 min", "1 hour", "2 hours", "6 hours", "12 hours", "24 hours")
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Sync Interval",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "How often to check for changes",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        // Simple dropdown using radio-like selection
        intervals.forEachIndexed { index, interval ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.RadioButton(
                    selected = currentInterval == interval,
                    onClick = { onIntervalSelected(interval) }
                )
                Text(
                    text = labels[index],
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun ConflictStrategySetting(
    currentStrategy: ConflictResolutionStrategy,
    onStrategySelected: (ConflictResolutionStrategy) -> Unit
) {
    val strategies = listOf(
        ConflictResolutionStrategy.ASK_USER to "Ask me each time",
        ConflictResolutionStrategy.KEEP_LOCAL to "Always keep local version",
        ConflictResolutionStrategy.KEEP_REMOTE to "Always keep Drive version",
        ConflictResolutionStrategy.KEEP_NEWEST to "Keep newest version",
        ConflictResolutionStrategy.KEEP_BOTH to "Keep both versions"
    )
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "When conflicts occur",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        strategies.forEach { (strategy, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.RadioButton(
                    selected = currentStrategy == strategy,
                    onClick = { onStrategySelected(strategy) }
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
