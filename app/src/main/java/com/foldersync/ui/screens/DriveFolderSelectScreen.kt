package com.foldersync.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldersync.data.local.PreferencesManager
import com.foldersync.data.remote.drive.DriveFileManager
import com.foldersync.data.repository.AuthRepository
import com.foldersync.domain.model.DriveFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DriveFolderSelectUiState(
    val isLoading: Boolean = false,
    val folders: List<DriveFile> = emptyList(),
    val currentFolderId: String = "root",
    val currentFolderName: String = "My Drive",
    val selectedFolder: DriveFile? = null,
    val error: String? = null,
    val breadcrumb: List<Pair<String, String>> = listOf("root" to "My Drive"),
    val needsAuthentication: Boolean = false
)

@HiltViewModel
class DriveFolderSelectViewModel @Inject constructor(
    private val driveFileManager: DriveFileManager,
    private val preferencesManager: PreferencesManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriveFolderSelectUiState())
    val uiState: StateFlow<DriveFolderSelectUiState> = _uiState

    init {
        checkAuthAndLoadFolders()
    }

    private fun checkAuthAndLoadFolders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, needsAuthentication = false) }
            
            val isAuthenticated = authRepository.isAuthenticated()
            if (!isAuthenticated) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        needsAuthentication = true,
                        error = null
                    ) 
                }
                return@launch
            }
            
            loadFoldersInternal("root")
        }
    }

    fun handleSignInResult(data: android.content.Intent?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = authRepository.handleSignInResult(data)
            when (result) {
                is com.foldersync.domain.model.AuthState.Authenticated -> {
                    checkAuthAndLoadFolders()
                }
                is com.foldersync.domain.model.AuthState.Error -> {
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            needsAuthentication = true,
                            error = result.message
                        ) 
                    }
                }
                is com.foldersync.domain.model.AuthState.SignedOut -> {
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            needsAuthentication = true,
                            error = null
                        ) 
                    }
                }
                else -> {
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            needsAuthentication = true,
                            error = "Sign-in failed. Please try again."
                        ) 
                    }
                }
            }
        }
    }

    fun getSignInIntent() = authRepository.getSignInIntent()

    fun loadFolders(folderId: String) {
        viewModelScope.launch {
            loadFoldersInternal(folderId)
        }
    }

    private suspend fun loadFoldersInternal(folderId: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        try {
            val folders = driveFileManager.listFolders(folderId)
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    folders = folders,
                    currentFolderId = folderId,
                    needsAuthentication = false
                )
            }
        } catch (e: Exception) {
            // Check if it's an authentication error
            val isAuthError = e.message?.contains("401") == true ||
                e.message?.contains("auth", ignoreCase = true) == true ||
                e.message?.contains("token", ignoreCase = true) == true
            
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    error = if (isAuthError) null else (e.message ?: "Failed to load folders"),
                    needsAuthentication = isAuthError
                )
            }
        }
    }

    fun navigateToFolder(folder: DriveFile) {
        _uiState.update { state ->
            state.copy(
                breadcrumb = state.breadcrumb + (folder.id to folder.name),
                currentFolderName = folder.name
            )
        }
        loadFolders(folder.id)
    }

    fun navigateBack() {
        val currentBreadcrumb = _uiState.value.breadcrumb
        if (currentBreadcrumb.size > 1) {
            val newBreadcrumb = currentBreadcrumb.dropLast(1)
            val (parentId, parentName) = newBreadcrumb.last()
            _uiState.update { 
                it.copy(
                    breadcrumb = newBreadcrumb,
                    currentFolderName = parentName
                )
            }
            loadFolders(parentId)
        }
    }

    fun selectCurrentFolder() {
        val state = _uiState.value
        viewModelScope.launch {
            preferencesManager.setDriveFolder(state.currentFolderId, state.currentFolderName)
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                driveFileManager.createFolder(name, _uiState.value.currentFolderId)
                loadFolders(_uiState.value.currentFolderId)
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to create folder"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveFolderSelectScreen(
    viewModel: DriveFolderSelectViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onFolderSelected: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    
    // Sign-in launcher - pass the Intent data to the ViewModel for proper handling
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleSignInResult(result.data)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.needsAuthentication) "Connect to Google Drive" else uiState.currentFolderName) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.breadcrumb.size > 1) {
                            viewModel.navigateBack()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            // Only show FABs when authenticated and not loading
            if (!uiState.needsAuthentication && !uiState.isLoading) {
                Column {
                    FloatingActionButton(
                        onClick = { showCreateDialog = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = "Create folder")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    FloatingActionButton(
                        onClick = {
                            viewModel.selectCurrentFolder()
                            onFolderSelected()
                        }
                    ) {
                        Icon(Icons.Rounded.Cloud, contentDescription = "Select this folder")
                    }
                }
            }
        }
    ) { paddingValues ->
        if (uiState.needsAuthentication) {
            // Show sign-in UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Cloud,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Connect to Google Drive",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sign in to your Google account to access and sync your Drive folders",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (uiState.error != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = uiState.error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        signInLauncher.launch(viewModel.getSignInIntent())
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Login,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign in with Google")
                }
            }
        } else if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = uiState.error!!,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.loadFolders(uiState.currentFolderId) }) {
                    Text("Retry")
                }
            }
        } else if (uiState.folders.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No subfolders",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Tap the cloud button to select this folder, or create a new subfolder",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(uiState.folders) { folder ->
                    DriveFolderItem(
                        folder = folder,
                        onClick = { viewModel.navigateToFolder(folder) }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                viewModel.createFolder(name)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun DriveFolderItem(
    folder: DriveFile,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Folder") },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text("Folder name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(folderName) },
                enabled = folderName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
