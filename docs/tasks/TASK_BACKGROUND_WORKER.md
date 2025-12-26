# Background Worker Component

## Overview
Implement background synchronization using WorkManager for reliable, battery-efficient periodic sync. This component handles scheduled sync operations, foreground services for long-running syncs, notifications, and automatic restart after device reboot.

## Tasks

### SyncWorker Implementation
- [ ] Create SyncWorker class extending CoroutineWorker
- [ ] Inject dependencies using HiltWorker
- [ ] Implement doWork() suspend function
- [ ] Check prerequisites (auth, network, folders configured)
- [ ] Call SyncEngine.sync()
- [ ] Handle sync result (success, failure, retry)
- [ ] Return appropriate Result (success, retry, failure)
- [ ] Implement progress reporting via setProgress()
- [ ] Handle cancellation with isStopped check

### WorkManager Periodic Work Request
- [ ] Create SyncScheduler class
- [ ] Define unique work name for sync
- [ ] Build PeriodicWorkRequest with configurable interval
- [ ] Set minimum interval (15 minutes per WorkManager limit)
- [ ] Configure flex interval for battery optimization
- [ ] Set ExistingPeriodicWorkPolicy (KEEP or REPLACE)
- [ ] Implement schedulePeriodic(interval: Duration)
- [ ] Implement cancelScheduledSync()
- [ ] Implement isScheduled() check

### Constraints Configuration
- [ ] Define network constraint (NetworkType.CONNECTED)
- [ ] Add Wi-Fi only constraint option (NetworkType.UNMETERED)
- [ ] Add battery not low constraint (optional)
- [ ] Add charging constraint option (optional)
- [ ] Add storage not low constraint
- [ ] Make constraints configurable via settings
- [ ] Build Constraints object dynamically

### Foreground Service for Long Syncs
- [ ] Implement setForegroundAsync() in SyncWorker
- [ ] Create ForegroundInfo with notification
- [ ] Define notification channel for sync
- [ ] Show ongoing notification during sync
- [ ] Display sync progress in notification
- [ ] Add cancel action to notification
- [ ] Handle foreground service type (dataSync)
- [ ] Properly stop foreground when complete

### Notifications Implementation
- [ ] Create NotificationHelper class
- [ ] Create notification channels:
  - [ ] sync_progress (low importance, ongoing)
  - [ ] sync_complete (default importance)
  - [ ] sync_error (high importance)
  - [ ] sync_conflict (high importance)
- [ ] Implement showSyncProgressNotification()
- [ ] Implement showSyncCompleteNotification()
- [ ] Implement showSyncErrorNotification()
- [ ] Implement showConflictNotification()
- [ ] Add notification actions (retry, view, dismiss)
- [ ] Handle notification tap (open app)
- [ ] Update notification with progress

### Boot Receiver for Auto-Start
- [ ] Create BootReceiver BroadcastReceiver
- [ ] Register in AndroidManifest for BOOT_COMPLETED
- [ ] Check if auto-sync is enabled in settings
- [ ] Re-schedule periodic sync after boot
- [ ] Handle QUICKBOOT_POWERON for some devices
- [ ] Add RECEIVE_BOOT_COMPLETED permission
- [ ] Test on various Android versions

### One-Time Sync Request
- [ ] Implement triggerImmediateSync()
- [ ] Create OneTimeWorkRequest
- [ ] Set expedited work for immediate execution
- [ ] Chain with periodic work if needed
- [ ] Return work ID for status tracking

### Work Status Observation
- [ ] Implement observeSyncStatus(): Flow<WorkInfo>
- [ ] Get work by unique name
- [ ] Map WorkInfo.State to UI-friendly state
- [ ] Extract progress data from WorkInfo
- [ ] Handle work completion/failure

### Error Handling and Retry
- [ ] Configure backoff policy (exponential)
- [ ] Set max retry attempts
- [ ] Handle specific error types differently:
  - [ ] Network error → retry with backoff
  - [ ] Auth error → stop and notify
  - [ ] Storage error → stop and notify
- [ ] Log errors for debugging
- [ ] Report errors to analytics (optional)

## Acceptance Criteria
- [ ] Periodic sync runs at configured intervals
- [ ] Sync respects network and battery constraints
- [ ] Long-running syncs show foreground notification
- [ ] Notifications accurately reflect sync status
- [ ] Sync resumes after device reboot
- [ ] Work can be cancelled from notification
- [ ] Battery impact is minimal
- [ ] Sync works in Doze mode (with expedited work)

## Dependencies on Other Components
| Component | Dependency Type | Description |
|-----------|-----------------|-------------|
| Sync Engine | Required | Core sync logic |
| Authentication | Required | Valid auth check |
| Local Storage | Required | Folder configuration |
| App Infrastructure | Required | Hilt for worker injection |
| UI Layer | Related | Displays sync status |

## Estimated Effort
| Task Category | Hours |
|---------------|-------|
| SyncWorker Implementation | 4 |
| Periodic Work Request | 3 |
| Constraints Configuration | 2 |
| Foreground Service | 4 |
| Notifications | 5 |
| Boot Receiver | 2 |
| One-Time Sync | 2 |
| Work Status Observation | 2 |
| Error Handling & Retry | 3 |
| Testing & Debugging | 5 |
| **Total** | **32** |

## Technical Notes
- Use `@HiltWorker` annotation for dependency injection
- WorkManager automatically handles Doze mode
- Minimum periodic interval is 15 minutes
- Use `setExpedited()` for immediate priority work
- Consider using `setInitialDelay()` for staggered starts
- Test with `adb shell dumpsys jobscheduler`
- Foreground service type must be declared in manifest

## Worker Lifecycle
```
[Enqueued] → [Running] → [Succeeded]
                ↓
           [Retrying] → [Running] → [Succeeded]
                ↓
           [Failed] (max retries exceeded)
           
[Running] → [Cancelled] (user or system)
```

## Manifest Permissions Required
```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"/>
```
