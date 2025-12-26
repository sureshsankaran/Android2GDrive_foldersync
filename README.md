# FolderSync

A modern Android application for bidirectional synchronization between local device folders and Google Drive. Built with Kotlin, Jetpack Compose, and Clean Architecture principles.

![Android](https://img.shields.io/badge/Android-26%2B-green)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.02-purple)
![License](https://img.shields.io/badge/License-MIT-yellow)

---

## 🎯 Use Case: Obsidian Vault Cloud Sync

**Perfect for syncing your [Obsidian](https://obsidian.md/) vault between devices using Google Drive!**

Obsidian stores notes as local Markdown files, but lacks built-in cloud sync on Android. FolderSync bridges this gap:

```
📱 Phone (Obsidian Vault)     ☁️ Google Drive      💻 Desktop (Obsidian Vault)
       /Obsidian/MyVault  ←→  /Obsidian/MyVault  ←→  ~/Documents/Obsidian/MyVault
```

### How It Works:
1. **On your phone**: Point FolderSync to your local Obsidian vault folder
2. **On Google Drive**: Select or create a folder for your vault
3. **Enable background sync**: Your notes sync automatically every 15 minutes
4. **On desktop**: Use Google Drive desktop app or Obsidian Git plugin to sync

### Why FolderSync for Obsidian?
- ✅ **Free** - No Obsidian Sync subscription needed ($96/year saved!)
- ✅ **Works offline** - Edit notes without internet, sync when connected
- ✅ **Conflict detection** - Never lose edits when the same note is changed on multiple devices
- ✅ **Markdown files stay local** - Full control over your data
- ✅ **Background sync** - Set it and forget it

### Setup for Obsidian:
1. Create your Obsidian vault in a folder like `/storage/emulated/0/Obsidian/MyVault`
2. In FolderSync, add a sync pair:
   - **Local Folder**: Your Obsidian vault folder
   - **Drive Folder**: Create `/Obsidian/MyVault` on Drive
3. Enable **Background Sync** with 15-minute interval
4. On desktop, sync the same Drive folder to your computer

---

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
  - [Clean Architecture Layers](#clean-architecture-layers)
  - [Package Structure](#package-structure)
  - [Key Components](#key-components)
  - [Data Flow](#data-flow)
  - [Dependency Injection](#dependency-injection)
- [Technical Stack](#technical-stack)
- [Prerequisites](#prerequisites)
- [Build Instructions](#build-instructions)
  - [1. Clone the Repository](#1-clone-the-repository)
  - [2. Configure Google Cloud Console](#2-configure-google-cloud-console)
  - [3. Configure Local Properties](#3-configure-local-properties)
  - [4. Build the APK](#4-build-the-apk)
- [Installation](#installation)
  - [Install via ADB](#install-via-adb)
  - [Install via Gradle](#install-via-gradle)
- [Usage Guide](#usage-guide)
  - [Initial Setup](#initial-setup)
  - [Manual Sync](#manual-sync)
  - [Background Sync](#background-sync)
  - [Conflict Resolution](#conflict-resolution)
  - [Settings](#settings)
- [Sync Logic](#sync-logic)
  - [Sync States](#sync-states)
  - [File Comparison](#file-comparison)
  - [Google Docs Handling](#google-docs-handling)
- [Database Schema](#database-schema)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

---

## Features

- 🔄 **Bidirectional Sync**: Sync files both ways between local folders and Google Drive
- 📁 **Subfolder Support**: Full recursive sync of nested folder structures
- 🔐 **Secure Authentication**: OAuth 2.0 with Google Sign-In and automatic token refresh
- ⚡ **Resumable Uploads/Downloads**: Large file transfers can resume after interruption
- 🔍 **Smart Conflict Detection**: Detects and handles conflicting changes on both sides
- 📄 **Google Docs Export**: Automatically exports Google Docs/Sheets/Slides to Office formats
- 🔄 **Background Sync**: Configurable periodic sync using WorkManager (1 min - 24 hours)
- 💾 **Database-Tracked State**: Accurate create/update/delete detection using Room database
- 🎨 **Material 3 UI**: Modern, clean interface with Jetpack Compose
- 🚦 **Rate Limiting**: Built-in exponential backoff for API rate limits
- 📱 **Auto-Resume on App Start**: Sync schedule automatically restores when app launches
- 🔋 **Battery-Friendly**: Smart constraints for background sync (WiFi-only, charging-only options)
- 🔄 **Update vs Create**: Intelligently updates existing files instead of creating duplicates

---

## Architecture

FolderSync follows **Clean Architecture** principles with clear separation of concerns across three layers.

### Clean Architecture Layers

```
┌─────────────────────────────────────────────────────────────────┐
│                      PRESENTATION LAYER                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │   Screens   │  │  ViewModels │  │       Navigation        │  │
│  │  (Compose)  │  │             │  │                         │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
├─────────────────────────────────────────────────────────────────┤
│                        DOMAIN LAYER                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │   Models    │  │  Use Cases  │  │      Sync Engine        │  │
│  │             │  │             │  │     (SyncDiffer)        │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
├─────────────────────────────────────────────────────────────────┤
│                         DATA LAYER                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │    Room     │  │  Retrofit   │  │     Repositories        │  │
│  │  Database   │  │ (Drive API) │  │                         │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Package Structure

```
com.foldersync/
├── FolderSyncApp.kt              # Application class (Hilt entry point)
├── auth/
│   └── GoogleAuthManager.kt      # Google Sign-In orchestration
├── core/
│   └── ...                       # Core utilities
├── data/
│   ├── auth/
│   │   └── TokenRefreshManager.kt    # OAuth token refresh with caching
│   ├── local/
│   │   ├── db/
│   │   │   ├── AppDatabase.kt        # Room database
│   │   │   ├── SyncFileDao.kt        # DAO for sync tracking
│   │   │   └── SyncLogDao.kt         # DAO for sync logs
│   │   ├── entity/
│   │   │   ├── SyncFileEntity.kt     # Tracked file state
│   │   │   └── SyncLogEntity.kt      # Sync operation logs
│   │   ├── FileSystemManager.kt      # SAF file operations
│   │   ├── ChecksumCalculator.kt     # MD5 checksum for files
│   │   └── PreferencesManager.kt     # DataStore preferences
│   ├── remote/
│   │   └── drive/
│   │       ├── DriveApiService.kt    # Retrofit API interface
│   │       ├── DriveFileManager.kt   # Drive operations facade
│   │       ├── AuthInterceptor.kt    # OAuth header injection
│   │       ├── RateLimiter.kt        # Exponential backoff
│   │       ├── model/                # API DTOs
│   │       ├── upload/               # Resumable upload logic
│   │       └── error/                # API exceptions
│   └── repository/
│       └── SyncRepository.kt         # Data layer facade
├── di/
│   ├── AppModule.kt                  # Core dependencies
│   ├── AuthModule.kt                 # Auth dependencies
│   ├── DatabaseModule.kt             # Room database
│   ├── NetworkModule.kt              # Retrofit/OkHttp
│   ├── RepositoryModule.kt           # Repositories
│   └── WorkerModule.kt               # WorkManager
├── domain/
│   ├── model/
│   │   ├── DriveFile.kt              # Domain model
│   │   ├── SyncPair.kt               # Folder pair config
│   │   └── SyncProgress.kt           # Sync state model
│   ├── sync/
│   │   ├── SyncDiffer.kt             # Diff algorithm
│   │   └── SyncEngineV2.kt           # Sync orchestrator
│   └── usecase/
│       └── ...                       # Business logic use cases
├── sync/
│   └── ...                           # Legacy sync components
├── ui/
│   ├── MainActivity.kt               # Single activity
│   ├── components/                   # Reusable Compose components
│   ├── navigation/                   # Navigation graph
│   ├── screens/
│   │   ├── HomeScreen.kt             # Main dashboard
│   │   ├── HomeViewModel.kt          # Home state management
│   │   ├── SettingsScreen.kt         # App settings
│   │   ├── SettingsViewModel.kt      # Settings state
│   │   ├── FolderSelectScreen.kt     # Local folder picker
│   │   ├── DriveFolderSelectScreen.kt # Drive folder picker
│   │   └── ConflictResolutionScreen.kt # Conflict UI
│   └── theme/                        # Material 3 theming
├── util/
│   └── ...                           # Utility extensions
└── worker/
    └── SyncWorker.kt                 # Background sync worker
```

### Key Components

#### SyncDiffer (`domain/sync/SyncDiffer.kt`)
The brain of the sync logic. Compares local files, Drive files, and database state to produce a diff:

```kotlin
data class SyncDiff(
    val localOnlyFiles: List<LocalFile>,      // New local files → upload
    val driveOnlyFiles: List<DriveFile>,      // New Drive files → download
    val modifiedFiles: List<ModifiedFile>,    // Changed files → sync
    val deletedLocal: List<SyncFileEntity>,   // Deleted locally → delete from Drive
    val deletedDrive: List<SyncFileEntity>,   // Deleted on Drive → delete local
    val conflicts: List<ConflictPair>,        // Both modified → user decision
    val pendingUploads: List<SyncFileEntity>, // Failed uploads to retry
    val pendingDownloads: List<SyncFileEntity> // Failed downloads to retry
)
```

#### SyncEngineV2 (`domain/sync/SyncEngineV2.kt`)
Orchestrates the sync process:
1. Fetches local and Drive file lists
2. Uses `SyncDiffer` to compute changes
3. Applies changes (upload/download/delete)
4. Updates database state
5. Handles errors with retry states

#### DriveFileManager (`data/remote/drive/DriveFileManager.kt`)
Facade for all Google Drive operations:
- File listing with pagination
- Resumable uploads with progress
- Downloads with range support
- Google Docs export to Office formats
- Folder creation and file deletion

### Data Flow

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   UI Layer   │───▶│ ViewModel    │───▶│  SyncEngine  │
│  (Compose)   │    │              │    │              │
└──────────────┘    └──────────────┘    └──────┬───────┘
                                               │
                    ┌──────────────────────────┼──────────────────────────┐
                    │                          │                          │
                    ▼                          ▼                          ▼
            ┌──────────────┐          ┌──────────────┐          ┌──────────────┐
            │FileSystemMgr │          │SyncRepository│          │DriveFileMgr  │
            │ (Local SAF)  │          │  (Database)  │          │ (Drive API)  │
            └──────────────┘          └──────────────┘          └──────────────┘
```

### Dependency Injection

Using **Hilt** for dependency injection with the following modules:

| Module             | Provides                                    |
| ------------------ | ------------------------------------------- |
| `AppModule`        | Application context, dispatchers, DataStore |
| `AuthModule`       | GoogleSignInClient, TokenRefreshManager     |
| `DatabaseModule`   | Room database, DAOs                         |
| `NetworkModule`    | OkHttpClient, Retrofit, DriveApiService     |
| `RepositoryModule` | SyncRepository                              |
| `WorkerModule`     | WorkManager configuration                   |

---

## Technical Stack

| Category         | Technology                      |
| ---------------- | ------------------------------- |
| **Language**     | Kotlin 1.9                      |
| **Min SDK**      | Android 8.0 (API 26)            |
| **Target SDK**   | Android 14 (API 34)             |
| **UI Framework** | Jetpack Compose with Material 3 |
| **Architecture** | MVVM + Clean Architecture       |
| **DI**           | Hilt (Dagger)                   |
| **Database**     | Room                            |
| **Networking**   | Retrofit + OkHttp               |
| **Background**   | WorkManager                     |
| **Auth**         | Google Sign-In (Play Services)  |
| **Storage**      | Storage Access Framework (SAF)  |
| **Preferences**  | DataStore                       |
| **Async**        | Kotlin Coroutines + Flow        |

---

## Prerequisites

1. **JDK 17** - Required for building
   ```bash
   # macOS (Homebrew)
   brew install openjdk@17
   
   # Verify
   /opt/homebrew/opt/openjdk@17/bin/java -version
   ```

2. **Android SDK** - API 34 (Android 14)

3. **Google Cloud Console Project** with:
   - Google Drive API enabled
   - OAuth 2.0 credentials configured

4. **Android Device or Emulator** - API 26+ with Google Play Services

---

## Build Instructions

### 1. Clone the Repository

```bash
git clone https://github.com/sureshsankaran/foldersync.git
cd foldersync
```

### 2. Configure Google Cloud Console

1. Go to [Google Cloud Console](https://console.cloud.google.com/)

2. Create a new project or select existing one

3. Enable the **Google Drive API**:
   - Navigate to "APIs & Services" → "Library"
   - Search for "Google Drive API"
   - Click "Enable"

4. Configure **OAuth Consent Screen**:
   - Navigate to "APIs & Services" → "OAuth consent screen"
   - Choose "External" user type
   - Fill in required fields (App name, User support email, Developer email)
   - Add scope: `https://www.googleapis.com/auth/drive`
   - Add test users if in testing mode

5. Create **OAuth 2.0 Credentials**:
   - Navigate to "APIs & Services" → "Credentials"
   - Click "Create Credentials" → "OAuth client ID"
   
   Create TWO credentials:
   
   **a) Android Client (for app signing):**
   - Application type: Android
   - Package name: `com.foldersync`
   - SHA-1 fingerprint: Get from your debug keystore
     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android
     ```
   
   **b) Web Client (for token exchange):**
   - Application type: Web application
   - Name: "FolderSync Web Client"
   - No redirect URIs needed
   - **Copy the Client ID** - you'll need this!

### 3. Configure Local Properties

Create/edit `local.properties` in the project root:

```properties
# SDK location (auto-generated by Android Studio)
sdk.dir=/Users/YOUR_USERNAME/Library/Android/sdk

# Google OAuth Web Client ID (from step 2.5b above)
WEB_CLIENT_ID=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
```

> ⚠️ **Security Note**: `local.properties` is gitignored. Never commit OAuth credentials.

### 4. Build the APK

```bash
# Set Java 17 and build
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug

# Output APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

**Build Variants:**

| Command                     | Description                             |
| --------------------------- | --------------------------------------- |
| `./gradlew assembleDebug`   | Debug build with debugging enabled      |
| `./gradlew assembleRelease` | Release build (requires signing config) |
| `./gradlew bundleRelease`   | Android App Bundle for Play Store       |

---

## Installation

### Install via ADB

```bash
# Ensure device is connected and USB debugging enabled
adb devices

# Install the APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Install via Gradle

```bash
# Build and install in one command
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew installDebug
```

**Troubleshooting Installation:**

```bash
# Uninstall existing version first
adb uninstall com.foldersync

# Install with verbose output
adb install -r -t app/build/outputs/apk/debug/app-debug.apk

# Check for errors
adb logcat | grep -i "install"
```

---

## Usage Guide

### Initial Setup

1. **Launch the app** - FolderSync icon appears in app drawer

2. **Sign in with Google**:
   - Tap "Sign in with Google" button
   - Select your Google account
   - Grant Drive access permissions

3. **Configure a Sync Pair**:
   - Tap the **"+"** button on the home screen
   - **Select Local Folder**: Choose a folder from your device
   - **Select Drive Folder**: Choose or create a folder in Google Drive
   - The sync pair appears on the home screen

### Manual Sync

1. From the **Home Screen**, locate your sync pair

2. Tap the **Sync** button (circular arrows icon)

3. Watch progress:
   - Progress bar shows overall completion
   - Status text shows current operation
   - File counts show processed/total

4. **Sync completes** with success or error notification

### Background Sync

Enable automatic periodic sync:

1. Open **Settings** (gear icon)

2. Toggle **"Enable Background Sync"**

3. Configure **sync interval**:
   - 1 minute (debug/testing)
   - 5 minutes (debug/testing)
   - 15 minutes
   - 30 minutes
   - 1 hour
   - 2 hours
   - 6 hours
   - 12 hours
   - 24 hours

4. Optional constraints:
   - **"WiFi Only"** - sync only on WiFi networks
   - **"Charging Only"** - sync only when device is charging

> **Note**: Intervals under 15 minutes use a special debug mode with chained one-time work requests instead of periodic work, as WorkManager has a 15-minute minimum for periodic tasks.

> **Note**: The sync schedule is automatically restored when the app starts, so you don't need to reconfigure after a device restart or app update.

### Conflict Resolution

When the same file is modified on both local and Drive:

1. **Conflict screen appears** during sync

2. For each conflict, choose:
   - **Keep Local** - Upload local version, overwrite Drive
   - **Keep Remote** - Download Drive version, overwrite local
   - **Keep Both** - Rename local file, keep both versions
   - **Skip** - Do nothing, handle later

3. Tap **"Apply"** to resolve conflicts

### Settings

| Setting                | Description                              |
| ---------------------- | ---------------------------------------- |
| **Background Sync**    | Enable/disable periodic sync             |
| **Sync Interval**      | How often to sync (1min - 24hr)          |
| **WiFi Only**          | Only sync on WiFi networks               |
| **Charging Only**      | Only sync when device is charging        |
| **Conflict Strategy**  | Default conflict resolution              |
| **Sign Out**           | Disconnect Google account                |
| **Clear Sync History** | Reset sync tracking database             |

---

## Known Limitations

### Android Background Restrictions

- **Android 12+ (API 31+)**: Background foreground service restrictions may cause sync to run without a notification when app is in background. Sync still works but won't show progress notification.

- **Samsung Devices**: Aggressive battery optimization (FreecessController) may delay background syncs. For best results:
  - Add FolderSync to battery optimization whitelist
  - Go to Settings → Apps → FolderSync → Battery → Unrestricted

- **WorkManager Minimum Interval**: Android's WorkManager has a 15-minute minimum for periodic tasks. Shorter intervals (1-5 min) use a workaround with chained one-time work requests.

---

## Sync Logic

### Sync States

Each tracked file can be in one of these states:

| State              | Description                          |
| ------------------ | ------------------------------------ |
| `SYNCED`           | File is synchronized, no changes     |
| `PENDING_UPLOAD`   | Local change needs upload            |
| `PENDING_DOWNLOAD` | Drive change needs download          |
| `ERROR`            | Sync failed, will retry              |
| `CONFLICT`         | Both sides changed, needs resolution |

### File Comparison

The sync engine compares files using:

1. **Existence Check**: Is file present locally and/or on Drive?
2. **Modification Time**: Last modified timestamp comparison
3. **Checksum (optional)**: MD5 hash for content verification
4. **Database State**: Previous known state from tracking database

**Decision Matrix:**

| Local    | Drive    | Database | Action            |
| -------- | -------- | -------- | ----------------- |
| New      | -        | -        | Upload            |
| -        | New      | -        | Download          |
| Modified | Same     | Synced   | Upload            |
| Same     | Modified | Synced   | Download          |
| Modified | Modified | Synced   | Conflict          |
| Deleted  | Exists   | Synced   | Delete from Drive |
| Exists   | Deleted  | Synced   | Delete local      |

### Google Docs Handling

Google Docs/Sheets/Slides files cannot be downloaded directly. They are automatically exported:

| Google Type     | Export Format     | Extension |
| --------------- | ----------------- | --------- |
| Google Docs     | Office Word       | `.docx`   |
| Google Sheets   | Office Excel      | `.xlsx`   |
| Google Slides   | Office PowerPoint | `.pptx`   |
| Google Drawings | PNG Image         | `.png`    |

---

## Database Schema

### SyncFileEntity

Tracks the state of each synchronized file:

```sql
CREATE TABLE sync_files (
    id TEXT PRIMARY KEY,           -- Unique file ID
    syncPairId TEXT NOT NULL,      -- Parent sync pair
    localPath TEXT NOT NULL,       -- Relative local path
    driveId TEXT,                  -- Google Drive file ID
    drivePath TEXT,                -- Relative Drive path
    localModified INTEGER,         -- Local modification timestamp
    driveModified INTEGER,         -- Drive modification timestamp
    localChecksum TEXT,            -- MD5 checksum
    driveChecksum TEXT,            -- Drive MD5 checksum
    status TEXT NOT NULL,          -- SyncStatus enum
    lastSyncTime INTEGER,          -- Last successful sync
    errorMessage TEXT              -- Last error if any
);
```

### SyncLogEntity

Logs all sync operations for debugging:

```sql
CREATE TABLE sync_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp INTEGER NOT NULL,
    action TEXT NOT NULL,          -- UPLOAD, DOWNLOAD, DELETE, etc.
    filePath TEXT NOT NULL,
    fileName TEXT NOT NULL,
    success INTEGER NOT NULL,
    errorMessage TEXT,
    bytesTransferred INTEGER,
    durationMs INTEGER
);
```

---

## Troubleshooting

### Common Issues

**1. "Sign-in failed" or "Authentication error"**
```
- Verify WEB_CLIENT_ID in local.properties matches Google Cloud Console
- Ensure SHA-1 fingerprint is registered for debug keystore
- Check that Google Drive API is enabled
- Verify test user is added in OAuth consent screen
```

**2. "Session expired" / Frequent re-auth**
```
- The app caches tokens and refreshes automatically
- If persisting, try: Settings → Sign Out → Sign In again
- Check device time is correct (OAuth tokens are time-sensitive)
```

**3. "Download failed: fileNotDownloadable"**
```
- This occurs for Google Docs/Sheets/Slides files
- Fixed in latest version - files are exported to Office formats
- Update to latest version if seeing this error
```

**4. "Rate limit exceeded"**
```
- The app has built-in rate limiting with exponential backoff
- If persisting, wait a few minutes and try again
- Large initial syncs may take time due to API limits
```

**5. Build fails with "Java version" error**
```bash
# Ensure you're using Java 17
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew clean assembleDebug
```

**6. PNG files downloaded as JPG**
```
- Fixed in latest version - MIME type is now inferred from file extension
- Update to latest version if seeing this issue
```

**7. Duplicate files created in Google Drive**
```
- Fixed in latest version - existing files are now updated instead of creating new copies
- The sync engine now uses updateFile() for existing files, uploadFile() for new files
```

**8. Background sync not triggering on Samsung**
```
- Samsung's aggressive battery optimization may block background work
- Solution: Add FolderSync to battery optimization whitelist
  Settings → Apps → FolderSync → Battery → Unrestricted
- Or use the 1-minute debug interval which uses chained OneTimeWork
```

**9. "ForegroundServiceStartNotAllowedException" in logs**
```
- This is expected on Android 12+ when sync runs from background
- The sync still works - this error is handled gracefully
- Only the progress notification won't show
```

### Viewing Logs

```bash
# All FolderSync logs
adb logcat | grep -E "FolderSync|SyncEngine|DriveFileManager"

# Sync operations only
adb logcat | grep -E "SyncEngineV2|SyncDiffer|SyncWorker"

# Background sync scheduling
adb logcat | grep -E "SyncScheduler|SyncWorker|FolderSyncApp"

# Auth issues
adb logcat | grep -E "TokenRefresh|GoogleAuth|AuthInterceptor"

# Clear and watch live
adb logcat -c && adb logcat | grep -i foldersync
```

---

## Version History

### v1.0.0 (December 2025)
- ✅ Bidirectional sync between local folders and Google Drive
- ✅ Subfolder support with recursive sync
- ✅ Google Docs/Sheets/Slides export to Office formats
- ✅ Background sync with configurable intervals (1 min - 24 hours)
- ✅ Auto-restore sync schedule on app start
- ✅ Conflict detection and resolution
- ✅ Database-tracked file state for accurate sync decisions
- ✅ Update existing files (no duplicate creation)
- ✅ Proper MIME type handling for all file types
- ✅ Graceful handling of delete failures (404/403)
- ✅ Samsung battery optimization workarounds

---

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push to branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

### Code Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful commit messages
- Add KDoc comments for public APIs
- Write unit tests for business logic

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Acknowledgments

- [Google Drive API](https://developers.google.com/drive/api/v3/about-sdk)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Hilt](https://dagger.dev/hilt/)
- [Room](https://developer.android.com/training/data-storage/room)

---

**Made with ❤️ by Suresh Sankaran**
