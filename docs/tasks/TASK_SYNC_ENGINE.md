# Sync Engine Component

## Overview
Implement the core synchronization logic for bidirectional sync between local folders and Google Drive. This component handles file comparison, conflict detection, conflict resolution, and orchestrates the sync process using the Local Storage and Drive API components.

## Tasks

### SyncEngine Core Implementation
- [ ] Create SyncEngine class
- [ ] Inject all required dependencies (repos, managers)
- [ ] Implement main sync() suspend function
- [ ] Define SyncResult data class (success, failures, conflicts)
- [ ] Implement sync state machine (IDLE, SCANNING, COMPARING, SYNCING, DONE)
- [ ] Emit sync progress as Flow<SyncProgress>
- [ ] Handle cancellation gracefully
- [ ] Implement sync locking (prevent concurrent syncs)

### FileDiffer (Compare Local vs Drive)
- [ ] Create FileDiffer class
- [ ] Implement buildLocalFileMap(localFiles: List<LocalFile>)
- [ ] Implement buildDriveFileMap(driveFiles: List<DriveFile>)
- [ ] Compare files by checksum (primary) and modifiedTime (secondary)
- [ ] Categorize files into:
  - [ ] NEW_LOCAL - exists only locally
  - [ ] NEW_REMOTE - exists only on Drive
  - [ ] MODIFIED_LOCAL - local is newer
  - [ ] MODIFIED_REMOTE - remote is newer
  - [ ] CONFLICT - both modified
  - [ ] UNCHANGED - identical
  - [ ] DELETED_LOCAL - removed locally
  - [ ] DELETED_REMOTE - removed from Drive
- [ ] Return DiffResult with categorized file lists

### ConflictResolver Logic
- [ ] Create ConflictResolver class
- [ ] Define ConflictResolutionStrategy enum:
  - [ ] KEEP_LOCAL - always prefer local version
  - [ ] KEEP_REMOTE - always prefer remote version
  - [ ] KEEP_NEWEST - prefer most recent modification
  - [ ] KEEP_BOTH - rename and keep both versions
  - [ ] ASK_USER - prompt for each conflict
- [ ] Implement resolveConflict(file, strategy) function
- [ ] Generate unique names for KEEP_BOTH strategy
- [ ] Track resolved conflicts for history
- [ ] Support batch conflict resolution

### SyncRepository Implementation
- [ ] Create SyncRepository interface
- [ ] Implement SyncRepositoryImpl
- [ ] Inject SyncEngine, DriveFileManager, FileSystemManager
- [ ] Expose syncState as StateFlow<SyncState>
- [ ] Implement startSync() function
- [ ] Implement cancelSync() function
- [ ] Implement getSyncHistory() function
- [ ] Implement getLastSyncTime() function
- [ ] Persist sync results to database

### SyncFolderUseCase
- [ ] Create SyncFolderUseCase class
- [ ] Inject SyncRepository
- [ ] Implement invoke(localUri, driveId) operator function
- [ ] Validate folder access before sync
- [ ] Check network connectivity
- [ ] Check authentication status
- [ ] Return Result<SyncResult> with success/failure
- [ ] Log sync events for analytics

### ResolveConflictUseCase
- [ ] Create ResolveConflictUseCase class
- [ ] Implement invoke(conflictId, resolution) function
- [ ] Apply user's resolution choice
- [ ] Update file sync status
- [ ] Trigger sync for resolved file
- [ ] Return Result<Unit>

### GetSyncStatusUseCase
- [ ] Create GetSyncStatusUseCase class
- [ ] Return current sync state
- [ ] Return pending file count
- [ ] Return conflict count
- [ ] Return last sync time
- [ ] Return sync statistics (totals)

### Bidirectional Sync Algorithm
- [ ] Implement full sync flow:
  1. [ ] Scan local folder
  2. [ ] List Drive folder contents
  3. [ ] Run FileDiffer
  4. [ ] Process NEW_LOCAL → upload to Drive
  5. [ ] Process NEW_REMOTE → download to local
  6. [ ] Process MODIFIED_LOCAL → update on Drive
  7. [ ] Process MODIFIED_REMOTE → update local
  8. [ ] Process CONFLICT → apply resolution strategy
  9. [ ] Process DELETED_LOCAL → delete from Drive (optional)
  10. [ ] Process DELETED_REMOTE → delete local (optional)
  11. [ ] Update sync metadata in database
  12. [ ] Record sync history
- [ ] Handle partial sync (resume after failure)
- [ ] Implement dry-run mode (preview changes)
- [ ] Support sync direction filter (upload-only, download-only)

## Acceptance Criteria
- [ ] Bidirectional sync works correctly
- [ ] File changes are detected accurately (checksum-based)
- [ ] Conflicts are detected and reported
- [ ] All resolution strategies work correctly
- [ ] Sync can be cancelled mid-operation
- [ ] Partial failures don't corrupt data
- [ ] Sync history is recorded accurately
- [ ] Performance is acceptable for 1000+ files

## Dependencies on Other Components
| Component | Dependency Type | Description |
|-----------|-----------------|-------------|
| Local Storage | Required | File tracking and metadata storage |
| Drive API | Required | Google Drive file operations |
| Authentication | Required | Valid auth for Drive access |
| UI Layer | Dependent | Displays sync status and conflicts |
| Background Worker | Dependent | Triggers automated sync |

## Estimated Effort
| Task Category | Hours |
|---------------|-------|
| SyncEngine Core | 6 |
| FileDiffer | 5 |
| ConflictResolver | 4 |
| SyncRepository | 4 |
| SyncFolderUseCase | 2 |
| ResolveConflictUseCase | 2 |
| GetSyncStatusUseCase | 1 |
| Bidirectional Sync Algorithm | 8 |
| Testing & Debugging | 8 |
| **Total** | **40** |

## Technical Notes
- Use coroutine `supervisorScope` for parallel file operations
- Implement proper transaction handling for database updates
- Consider using a work queue for file operations
- Add telemetry for sync performance monitoring
- Handle edge cases: empty folders, special characters in names
- Implement file size limits for sync (configurable)

## Sync State Diagram
```
[IDLE] → startSync() → [SCANNING] → [COMPARING] → [SYNCING] → [COMPLETED]
                              ↓            ↓            ↓
                          [ERROR]      [ERROR]      [ERROR]
                              ↓            ↓            ↓
                          [IDLE]       [IDLE]       [IDLE]
                          
[ANY STATE] → cancelSync() → [CANCELLED] → [IDLE]
```
