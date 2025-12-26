# FolderSync - Android Google Drive Sync App

## Specification Document
**Version:** 1.0  
**Date:** December 25, 2025  
**Status:** Draft

---

## 1. Overview

### 1.1 Purpose
FolderSync is a simple Android application that enables bidirectional synchronization between a local folder on an Android device and a folder in Google Drive. Users can designate a folder to sync, and changes made on either the device or Google Drive will be reflected on both platforms.

### 1.2 Scope
- **Platform:** Android (API 26+, Android 8.0 Oreo and above)
- **Cloud Provider:** Google Drive only
- **Sync Type:** Bidirectional (Local ↔ Cloud)
- **Folder Scope:** Single folder with all nested files (recursive)

### 1.3 Goals
- Simple, intuitive user interface
- Reliable bidirectional sync
- Efficient battery and data usage
- Conflict resolution handling
- Background sync support

---

## 2. Features

### 2.1 Core Features

| Feature                | Description                          | Priority |
| ---------------------- | ------------------------------------ | -------- |
| Google Sign-In         | OAuth 2.0 authentication with Google | P0       |
| Folder Selection       | Select local folder to sync          | P0       |
| Drive Folder Selection | Select/create Google Drive folder    | P0       |
| Manual Sync            | Trigger sync on demand               | P0       |
| Bidirectional Sync     | Sync changes both ways               | P0       |
| Auto Sync              | Background periodic sync             | P1       |
| Conflict Resolution    | Handle file conflicts                | P1       |
| Sync Status            | Show sync progress and status        | P1       |
| Sync History           | Log of sync operations               | P2       |
| Selective Sync         | Exclude files/patterns               | P2       |

### 2.2 Feature Details

#### 2.2.1 Google Sign-In
- Use Google Sign-In SDK with Drive API scope
- Request scopes: `drive.file` (access to app-created files) or `drive` (full access)
- Secure token storage using Android Keystore
- Handle token refresh automatically

#### 2.2.2 Folder Selection (Local)
- Use Storage Access Framework (SAF) for folder picking
- Request `MANAGE_EXTERNAL_STORAGE` or use scoped storage
- Display selected folder path and file count
- Persist folder URI across app restarts

#### 2.2.3 Google Drive Folder Selection
- Browse Drive folders in-app
- Create new folder option
- Display folder path in Drive

#### 2.2.4 Bidirectional Sync Logic

```
┌─────────────────────────────────────────────────────────────┐
│                    SYNC FLOW DIAGRAM                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────┐                           ┌─────────┐         │
│  │  Local  │                           │  Drive  │         │
│  │ Folder  │                           │ Folder  │         │
│  └────┬────┘                           └────┬────┘         │
│       │                                     │               │
│       ▼                                     ▼               │
│  ┌─────────┐                           ┌─────────┐         │
│  │  Scan   │                           │  Scan   │         │
│  │  Files  │                           │  Files  │         │
│  └────┬────┘                           └────┬────┘         │
│       │                                     │               │
│       └──────────────┬──────────────────────┘               │
│                      ▼                                      │
│              ┌───────────────┐                              │
│              │   Compare &   │                              │
│              │   Diff Files  │                              │
│              └───────┬───────┘                              │
│                      │                                      │
│       ┌──────────────┼──────────────┐                       │
│       ▼              ▼              ▼                       │
│  ┌─────────┐   ┌──────────┐   ┌─────────┐                  │
│  │ Upload  │   │ Download │   │ Resolve │                  │
│  │  New/   │   │  New/    │   │Conflicts│                  │
│  │Modified │   │ Modified │   │         │                  │
│  └─────────┘   └──────────┘   └─────────┘                  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

#### 2.2.5 Conflict Resolution Strategy

| Scenario                      | Default Resolution        | User Option    |
| ----------------------------- | ------------------------- | -------------- |
| Both modified                 | Keep newer (by timestamp) | Choose version |
| Local deleted, Drive modified | Keep Drive version        | Delete both    |
| Drive deleted, Local modified | Keep Local version        | Delete both    |
| Both deleted                  | No action needed          | N/A            |

---

## 3. Technical Architecture

### 3.1 Tech Stack

| Component       | Technology                |
| --------------- | ------------------------- |
| Language        | Kotlin                    |
| Min SDK         | 26 (Android 8.0)          |
| Target SDK      | 34 (Android 14)           |
| UI Framework    | Jetpack Compose           |
| Architecture    | MVVM + Clean Architecture |
| DI              | Hilt                      |
| Local DB        | Room                      |
| Networking      | Retrofit + OkHttp         |
| Google APIs     | Google Drive API v3       |
| Background Work | WorkManager               |
| Coroutines      | Kotlin Coroutines + Flow  |

### 3.2 Project Structure

```
app/
├── src/main/
│   ├── java/com/foldersync/
│   │   ├── data/
│   │   │   ├── local/
│   │   │   │   ├── db/
│   │   │   │   │   ├── AppDatabase.kt
│   │   │   │   │   ├── SyncFileDao.kt
│   │   │   │   │   └── SyncHistoryDao.kt
│   │   │   │   ├── entity/
│   │   │   │   │   ├── SyncFileEntity.kt
│   │   │   │   │   └── SyncHistoryEntity.kt
│   │   │   │   └── FileSystemManager.kt
│   │   │   ├── remote/
│   │   │   │   ├── DriveApiService.kt
│   │   │   │   └── DriveFileManager.kt
│   │   │   └── repository/
│   │   │       ├── SyncRepository.kt
│   │   │       └── AuthRepository.kt
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   │   ├── SyncFile.kt
│   │   │   │   ├── SyncStatus.kt
│   │   │   │   └── ConflictInfo.kt
│   │   │   ├── usecase/
│   │   │   │   ├── SyncFolderUseCase.kt
│   │   │   │   ├── ResolveConflictUseCase.kt
│   │   │   │   └── GetSyncStatusUseCase.kt
│   │   │   └── sync/
│   │   │       ├── SyncEngine.kt
│   │   │       ├── FileDiffer.kt
│   │   │       └── ConflictResolver.kt
│   │   ├── ui/
│   │   │   ├── theme/
│   │   │   ├── screens/
│   │   │   │   ├── home/
│   │   │   │   ├── settings/
│   │   │   │   ├── folderselect/
│   │   │   │   └── conflicts/
│   │   │   └── components/
│   │   ├── worker/
│   │   │   └── SyncWorker.kt
│   │   ├── di/
│   │   │   └── AppModule.kt
│   │   └── FolderSyncApp.kt
│   ├── res/
│   └── AndroidManifest.xml
├── build.gradle.kts
└── proguard-rules.pro
```

### 3.3 Data Models

#### 3.3.1 SyncFile Entity (Room)
```kotlin
@Entity(tableName = "sync_files")
data class SyncFileEntity(
    @PrimaryKey val localPath: String,
    val driveFileId: String?,
    val fileName: String,
    val isDirectory: Boolean,
    val localModifiedTime: Long,
    val driveModifiedTime: Long?,
    val localChecksum: String?,
    val driveChecksum: String?,
    val syncStatus: SyncStatus,
    val lastSyncTime: Long?
)

enum class SyncStatus {
    SYNCED,
    LOCAL_MODIFIED,
    DRIVE_MODIFIED,
    CONFLICT,
    LOCAL_ONLY,
    DRIVE_ONLY,
    PENDING_UPLOAD,
    PENDING_DOWNLOAD,
    ERROR
}
```

#### 3.3.2 Sync History Entity
```kotlin
@Entity(tableName = "sync_history")
data class SyncHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val action: SyncAction,
    val filePath: String,
    val status: String,
    val errorMessage: String?
)

enum class SyncAction {
    UPLOAD, DOWNLOAD, DELETE_LOCAL, DELETE_DRIVE, CONFLICT_RESOLVED
}
```

### 3.4 Key Algorithms

#### 3.4.1 File Comparison Algorithm
```
For each file in (LocalFiles ∪ DriveFiles):
  1. Compute state based on existence and checksums
  2. Compare local vs drive modified timestamps
  3. Determine sync action:
     - LocalOnly + not in deletions → Upload
     - DriveOnly + not in deletions → Download  
     - Both exist + checksums differ:
       - Local newer → Upload
       - Drive newer → Download
       - Same time → Conflict
     - Both exist + checksums match → Synced
```

#### 3.4.2 Checksum Strategy
- Use MD5 for file integrity (matches Drive's md5Checksum)
- Cache checksums to avoid recalculation
- Compute incrementally for large files

---

## 4. API Integration

### 4.1 Google Drive API Endpoints

| Operation         | API Method                      |
| ----------------- | ------------------------------- |
| List files        | `files.list` with query         |
| Get file metadata | `files.get`                     |
| Upload file       | `files.create` (multipart)      |
| Update file       | `files.update`                  |
| Download file     | `files.get` with `alt=media`    |
| Delete file       | `files.delete`                  |
| Create folder     | `files.create` with folder MIME |

### 4.2 Required OAuth Scopes
```
https://www.googleapis.com/auth/drive.file
// OR for full access:
https://www.googleapis.com/auth/drive
```

### 4.3 Rate Limiting
- Google Drive API: 1,000 queries per 100 seconds per user
- Implement exponential backoff for 403/429 errors
- Batch API calls where possible

---

## 5. User Interface

### 5.1 Screen Wireframes

#### 5.1.1 Home Screen
```
┌─────────────────────────────────┐
│  FolderSync              ⚙️    │
├─────────────────────────────────┤
│                                 │
│  ┌───────────────────────────┐  │
│  │ 📁 Local Folder           │  │
│  │ /storage/.../MySync       │  │
│  │ 245 files • 1.2 GB        │  │
│  └───────────────────────────┘  │
│                                 │
│           ⇅                     │
│                                 │
│  ┌───────────────────────────┐  │
│  │ ☁️ Google Drive           │  │
│  │ /MySync                   │  │
│  │ 243 files • 1.1 GB        │  │
│  └───────────────────────────┘  │
│                                 │
│  Last sync: 5 minutes ago       │
│  Status: ✅ Up to date          │
│                                 │
│  ┌───────────────────────────┐  │
│  │      🔄 SYNC NOW          │  │
│  └───────────────────────────┘  │
│                                 │
│  ⚠️ 2 conflicts need attention  │
│                                 │
└─────────────────────────────────┘
```

#### 5.1.2 Settings Screen
```
┌─────────────────────────────────┐
│  ← Settings                     │
├─────────────────────────────────┤
│                                 │
│  SYNC OPTIONS                   │
│  ─────────────────────────────  │
│  Auto sync              [ON ]   │
│  Sync interval          15 min  │
│  Sync on Wi-Fi only     [ON ]   │
│  Sync while charging    [OFF]   │
│                                 │
│  CONFLICT RESOLUTION            │
│  ─────────────────────────────  │
│  Default: Keep newer            │
│  ○ Keep newer                   │
│  ○ Keep local                   │
│  ○ Keep cloud                   │
│  ○ Always ask                   │
│                                 │
│  ACCOUNT                        │
│  ─────────────────────────────  │
│  user@gmail.com                 │
│  [Sign Out]                     │
│                                 │
│  ABOUT                          │
│  ─────────────────────────────  │
│  Version 1.0.0                  │
│  View sync history →            │
│                                 │
└─────────────────────────────────┘
```

#### 5.1.3 Conflict Resolution Screen
```
┌─────────────────────────────────┐
│  ← Resolve Conflicts            │
├─────────────────────────────────┤
│                                 │
│  2 files have conflicts         │
│                                 │
│  ┌───────────────────────────┐  │
│  │ 📄 report.docx            │  │
│  │                           │  │
│  │ Local         Cloud       │  │
│  │ Dec 25        Dec 24      │  │
│  │ 2.3 MB        2.1 MB      │  │
│  │                           │  │
│  │ [Keep Local] [Keep Cloud] │  │
│  │      [Keep Both]          │  │
│  └───────────────────────────┘  │
│                                 │
│  ┌───────────────────────────┐  │
│  │ 📄 notes.txt              │  │
│  │ ...                       │  │
│  └───────────────────────────┘  │
│                                 │
└─────────────────────────────────┘
```

---

## 6. Background Sync

### 6.1 WorkManager Configuration
```kotlin
val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
    repeatInterval = 15,
    repeatIntervalTimeUnit = TimeUnit.MINUTES
)
    .setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
    )
    .setBackoffCriteria(
        BackoffPolicy.EXPONENTIAL,
        WorkRequest.MIN_BACKOFF_MILLIS,
        TimeUnit.MILLISECONDS
    )
    .build()
```

### 6.2 Sync Triggers
1. **Manual**: User taps "Sync Now"
2. **Periodic**: WorkManager every 15/30/60 minutes
3. **On Network Change**: When Wi-Fi connects (optional)
4. **File Observer**: When local files change (using FileObserver)

---

## 7. Security Considerations

### 7.1 Authentication
- Store OAuth tokens in Android Keystore
- Use EncryptedSharedPreferences for settings
- Implement token refresh before expiry

### 7.2 Data Protection
- Use HTTPS for all API calls (enforced by Google)
- Don't log sensitive file contents
- Clear cached data on sign-out

### 7.3 Permissions
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

---

## 8. Error Handling

### 8.1 Error Categories

| Category | Examples                     | Handling                            |
| -------- | ---------------------------- | ----------------------------------- |
| Network  | No internet, timeout         | Retry with backoff, queue for later |
| Auth     | Token expired, revoked       | Re-authenticate, prompt user        |
| Storage  | Disk full, permission denied | Notify user, pause sync             |
| API      | Rate limited, quota exceeded | Backoff, notify user                |
| Conflict | Both sides modified          | Prompt user for resolution          |

### 8.2 Retry Strategy
```
Attempt 1: Immediate
Attempt 2: 1 second delay
Attempt 3: 2 seconds delay
Attempt 4: 4 seconds delay
Attempt 5: 8 seconds delay
Max retries: 5
```

---

## 9. Testing Strategy

### 9.1 Unit Tests
- SyncEngine logic
- FileDiffer comparison
- ConflictResolver decisions
- Checksum calculations

### 9.2 Integration Tests
- Room database operations
- Google Drive API mocking
- WorkManager scheduling

### 9.3 UI Tests
- Compose UI testing
- Navigation flows
- User interaction scenarios

### 9.4 Manual Testing Scenarios
1. Fresh install → Sign in → Select folders → Initial sync
2. Add file locally → Verify upload
3. Add file in Drive → Verify download
4. Modify same file both sides → Verify conflict detection
5. Delete file locally → Verify Drive deletion
6. Offline edit → Reconnect → Verify sync
7. Large file (>100MB) sync
8. Many files (>1000) sync

---

## 10. Milestones & Timeline

### Phase 1: Foundation (Week 1-2)
- [ ] Project setup with dependencies
- [ ] Google Sign-In integration
- [ ] Basic UI screens (Home, Settings)
- [ ] Local folder selection (SAF)
- [ ] Room database setup

### Phase 2: Core Sync (Week 3-4)
- [ ] Google Drive API integration
- [ ] Drive folder browsing/selection
- [ ] File listing (local + Drive)
- [ ] Basic upload functionality
- [ ] Basic download functionality

### Phase 3: Bidirectional Sync (Week 5-6)
- [ ] Sync engine implementation
- [ ] File diff algorithm
- [ ] Checksum comparison
- [ ] Delete sync (both directions)
- [ ] Sync status tracking

### Phase 4: Conflict & Polish (Week 7-8)
- [ ] Conflict detection
- [ ] Conflict resolution UI
- [ ] Background sync (WorkManager)
- [ ] Notifications
- [ ] Error handling & retry

### Phase 5: Testing & Release (Week 9-10)
- [ ] Unit tests
- [ ] Integration tests
- [ ] UI tests
- [ ] Performance optimization
- [ ] Beta testing
- [ ] Play Store submission

---

## 11. Dependencies

### 11.1 Gradle Dependencies
```kotlin
// build.gradle.kts (app)
dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.6")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    implementation("androidx.hilt:hilt-work:1.1.0")
    
    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Google Sign-In & Drive
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("com.google.api-client:google-api-client-android:2.2.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20231128-2.0.0")
    
    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
```

---

## 12. Future Enhancements (Out of Scope v1)

- Multiple folder sync pairs
- Selective file type sync
- Bandwidth throttling
- Sync scheduling (time-based)
- Encryption at rest
- Other cloud providers (OneDrive, Dropbox)
- Wear OS companion app
- Widgets
- File versioning history

---

## 13. Glossary

| Term               | Definition                                                 |
| ------------------ | ---------------------------------------------------------- |
| SAF                | Storage Access Framework - Android API for file access     |
| OAuth              | Open Authorization - authentication protocol               |
| Checksum           | Hash value to verify file integrity                        |
| Bidirectional Sync | Changes sync both from device to cloud and cloud to device |
| Conflict           | When same file modified on both sides since last sync      |
| WorkManager        | Android API for reliable background work                   |

---

## Appendix A: Google Cloud Console Setup

1. Create project in Google Cloud Console
2. Enable Google Drive API
3. Configure OAuth consent screen
4. Create OAuth 2.0 Client ID (Android type)
5. Add SHA-1 fingerprint from keystore
6. Download credentials JSON

---

*Document maintained by: FolderSync Development Team*
