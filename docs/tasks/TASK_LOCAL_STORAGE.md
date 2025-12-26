# Local Storage Component

## Overview
Implement local data persistence using Room database and file system access using Storage Access Framework (SAF). This component manages sync metadata, file tracking, history logging, and secure access to user-selected folders on the device.

## Tasks

### Room Database Setup (AppDatabase)
- [ ] Add Room dependencies to build.gradle.kts
- [ ] Create AppDatabase abstract class
- [ ] Configure database version and migrations
- [ ] Implement database singleton with Hilt
- [ ] Add TypeConverters for custom types (Date, Uri, etc.)
- [ ] Configure database inspector for debugging

### SyncFileEntity Implementation
- [ ] Create SyncFileEntity data class
- [ ] Define primary key (composite: localPath + driveId)
- [ ] Add fields: fileName, localPath, driveId, localChecksum, driveChecksum
- [ ] Add fields: localModifiedTime, driveModifiedTime
- [ ] Add fields: syncStatus (SYNCED, PENDING, CONFLICT, ERROR)
- [ ] Add fields: fileSize, mimeType, isDirectory
- [ ] Add indices for common queries

### SyncHistoryEntity Implementation
- [ ] Create SyncHistoryEntity data class
- [ ] Define auto-generated primary key
- [ ] Add fields: syncId, startTime, endTime, status
- [ ] Add fields: filesUploaded, filesDownloaded, filesDeleted
- [ ] Add fields: errorMessage, conflictsResolved
- [ ] Add foreign key relationship if needed

### SyncFileDao Implementation
- [ ] Create SyncFileDao interface
- [ ] Implement insert/upsert operations
- [ ] Implement getAll() with Flow return type
- [ ] Implement getByLocalPath(path: String)
- [ ] Implement getByDriveId(driveId: String)
- [ ] Implement getByStatus(status: SyncStatus)
- [ ] Implement getPendingFiles()
- [ ] Implement getConflictedFiles()
- [ ] Implement delete operations
- [ ] Implement updateSyncStatus()

### SyncHistoryDao Implementation
- [ ] Create SyncHistoryDao interface
- [ ] Implement insert operation
- [ ] Implement getAll() with Flow and pagination
- [ ] Implement getLatestSync()
- [ ] Implement getByDateRange(start, end)
- [ ] Implement clearOldHistory(olderThan: Date)

### FileSystemManager (SAF Integration)
- [ ] Create FileSystemManager class
- [ ] Implement folder picker intent creation
- [ ] Handle persistable URI permissions
- [ ] Implement takePersistableUriPermission()
- [ ] Check if permission is still valid
- [ ] Handle permission revocation gracefully

### Local Folder Scanning
- [ ] Implement scanFolder(uri: Uri) function
- [ ] Use DocumentFile API for SAF compatibility
- [ ] Recursively scan subdirectories
- [ ] Filter files by supported types (optional)
- [ ] Extract file metadata (name, size, modified time)
- [ ] Handle large folders with batching
- [ ] Emit progress updates during scan

### File Checksum Calculation (MD5)
- [ ] Implement calculateChecksum(uri: Uri) function
- [ ] Use MessageDigest with MD5 algorithm
- [ ] Stream file content for memory efficiency
- [ ] Handle large files without OOM
- [ ] Cache checksums to avoid recalculation
- [ ] Implement batch checksum calculation

### Persist Folder URI
- [ ] Create FolderPreferences DataStore
- [ ] Store selected local folder URI
- [ ] Store selected Drive folder ID
- [ ] Validate URI on app startup
- [ ] Handle missing/invalid stored URIs
- [ ] Provide migration from SharedPreferences

## Acceptance Criteria
- [ ] Room database initializes correctly
- [ ] All entities are properly stored and retrieved
- [ ] DAOs support reactive queries with Flow
- [ ] SAF folder picker works on all Android versions (API 21+)
- [ ] URI permissions persist across app restarts
- [ ] Folder scanning handles 10,000+ files
- [ ] Checksum calculation is accurate and efficient
- [ ] Database migrations work without data loss

## Dependencies on Other Components
| Component | Dependency Type | Description |
|-----------|-----------------|-------------|
| App Infrastructure | Required | Hilt modules for DI |
| Sync Engine | Dependent | Uses storage for file tracking |
| UI Layer | Dependent | Displays folder and file info |
| Background Worker | Dependent | Accesses files for background sync |

## Estimated Effort
| Task Category | Hours |
|---------------|-------|
| Room Database Setup | 2 |
| Entity Implementations | 3 |
| DAO Implementations | 4 |
| FileSystemManager (SAF) | 5 |
| Local Folder Scanning | 4 |
| Checksum Calculation | 3 |
| Persist Folder URI | 2 |
| Testing & Debugging | 5 |
| **Total** | **28** |

## Technical Notes
- Use `DocumentFile.fromTreeUri()` for SAF folder access
- Always check `contentResolver.persistedUriPermissions` on startup
- Consider using WorkManager for checksum calculation of large files
- Implement database export/import for backup functionality
- Use `@Transaction` for complex DAO operations
