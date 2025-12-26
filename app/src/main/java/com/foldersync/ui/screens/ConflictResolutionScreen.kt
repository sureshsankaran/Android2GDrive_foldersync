package com.foldersync.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldersync.data.local.db.SyncFileDao
import com.foldersync.domain.sync.ConflictInfo
import com.foldersync.domain.sync.ConflictResolutionStrategy
import com.foldersync.domain.usecase.ResolveConflictUseCase
import com.foldersync.ui.components.ConflictCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConflictResolutionUiState(
    val isLoading: Boolean = true,
    val conflicts: List<ConflictInfo> = emptyList(),
    val resolvedCount: Int = 0,
    val isResolvingAll: Boolean = false
)

@HiltViewModel
class ConflictResolutionViewModel @Inject constructor(
    private val syncFileDao: SyncFileDao,
    private val resolveConflictUseCase: ResolveConflictUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConflictResolutionUiState())
    val uiState: StateFlow<ConflictResolutionUiState> = _uiState

    init {
        loadConflicts()
    }

    private fun loadConflicts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            syncFileDao.getConflicts().collect { files ->
                val conflicts = files.map { file ->
                    ConflictInfo(
                        fileName = file.fileName,
                        localPath = file.localPath,
                        driveFileId = file.driveFileId ?: "",
                        localModifiedTime = file.localModifiedTime,
                        driveModifiedTime = file.driveModifiedTime ?: 0L,
                        localSize = file.fileSize,
                        driveSize = file.fileSize, // Would need to fetch from Drive
                        localChecksum = file.localChecksum,
                        driveChecksum = file.driveChecksum
                    )
                }
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        conflicts = conflicts
                    )
                }
            }
        }
    }

    fun resolveConflict(conflict: ConflictInfo, strategy: ConflictResolutionStrategy) {
        viewModelScope.launch {
            val action = resolveConflictUseCase(conflict, strategy)
            // Apply the resolution action
            // This would trigger the actual file operation
            _uiState.update { state ->
                state.copy(
                    conflicts = state.conflicts.filter { it.localPath != conflict.localPath },
                    resolvedCount = state.resolvedCount + 1
                )
            }
        }
    }

    fun resolveAllWithStrategy(strategy: ConflictResolutionStrategy) {
        viewModelScope.launch {
            _uiState.update { it.copy(isResolvingAll = true) }
            val conflicts = _uiState.value.conflicts
            conflicts.forEach { conflict ->
                resolveConflict(conflict, strategy)
            }
            _uiState.update { it.copy(isResolvingAll = false) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictResolutionScreen(
    viewModel: ConflictResolutionViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resolve Conflicts") },
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
        when {
            uiState.isLoading -> {
                LoadingContent(paddingValues)
            }
            uiState.conflicts.isEmpty() -> {
                EmptyConflictsContent(
                    paddingValues = paddingValues,
                    resolvedCount = uiState.resolvedCount,
                    onBack = onBack
                )
            }
            else -> {
                ConflictListContent(
                    paddingValues = paddingValues,
                    conflicts = uiState.conflicts,
                    isResolvingAll = uiState.isResolvingAll,
                    onResolve = viewModel::resolveConflict,
                    onResolveAll = viewModel::resolveAllWithStrategy
                )
            }
        }
    }
}

@Composable
private fun LoadingContent(paddingValues: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text("Loading conflicts...")
    }
}

@Composable
private fun EmptyConflictsContent(
    paddingValues: PaddingValues,
    resolvedCount: Int,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "All conflicts resolved!",
            style = MaterialTheme.typography.headlineSmall
        )
        if (resolvedCount > 0) {
            Text(
                text = "You resolved $resolvedCount conflict${if (resolvedCount > 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack) {
            Text("Go Back")
        }
    }
}

@Composable
private fun ConflictListContent(
    paddingValues: PaddingValues,
    conflicts: List<ConflictInfo>,
    isResolvingAll: Boolean,
    onResolve: (ConflictInfo, ConflictResolutionStrategy) -> Unit,
    onResolveAll: (ConflictResolutionStrategy) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${conflicts.size} conflict${if (conflicts.size > 1) "s" else ""} detected",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Choose which version to keep for each file",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        items(conflicts) { conflict ->
            ConflictCard(
                conflict = conflict,
                onResolve = { strategy -> onResolve(conflict, strategy) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Resolve all at once:",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Button(
                onClick = { onResolveAll(ConflictResolutionStrategy.KEEP_LOCAL) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isResolvingAll
            ) {
                Text("Keep All Local Versions")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { onResolveAll(ConflictResolutionStrategy.KEEP_REMOTE) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isResolvingAll
            ) {
                Text("Keep All Drive Versions")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onResolveAll(ConflictResolutionStrategy.KEEP_BOTH) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isResolvingAll
            ) {
                Text("Keep All Versions (Rename)")
            }
        }
    }
}
