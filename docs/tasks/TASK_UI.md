# UI Layer Component

## Overview
Implement the user interface for FolderSync using Jetpack Compose with Material 3 design. This component includes all screens, navigation, shared components, and theming to provide a modern, intuitive user experience.

## Tasks

### Theme Setup (Material 3)
- [ ] Create Theme.kt with Material 3 theme
- [ ] Define color schemes (light and dark)
- [ ] Create custom color palette for sync states
- [ ] Define typography scale
- [ ] Create shape definitions
- [ ] Implement dynamic color (Android 12+)
- [ ] Add theme preview composables

### Color Tokens
- [ ] Define semantic colors:
  - [ ] syncSuccess (green)
  - [ ] syncPending (amber)
  - [ ] syncError (red)
  - [ ] syncConflict (orange)
  - [ ] syncIdle (gray)

### HomeScreen Implementation
- [ ] Create HomeScreen composable
- [ ] Create HomeViewModel with StateFlow
- [ ] Display sync status card (last sync time, status)
- [ ] Display local folder info (path, file count, size)
- [ ] Display Drive folder info (name, file count)
- [ ] Add prominent "Sync Now" button
- [ ] Show sync progress indicator during sync
- [ ] Display pending changes count
- [ ] Display conflict count with navigation to resolve
- [ ] Add pull-to-refresh gesture
- [ ] Handle empty state (no folders selected)

### SettingsScreen Implementation
- [ ] Create SettingsScreen composable
- [ ] Create SettingsViewModel
- [ ] Auto-sync toggle with interval selection
- [ ] Sync interval options (15min, 30min, 1hr, 4hr, daily)
- [ ] Wi-Fi only sync toggle
- [ ] Conflict resolution default preference
- [ ] Notification preferences
- [ ] Storage usage display
- [ ] Clear sync history option
- [ ] Sign out button
- [ ] About section (version, licenses)

### FolderSelectScreen (Local Folder Picker)
- [ ] Create FolderSelectScreen composable
- [ ] Launch SAF folder picker intent
- [ ] Display currently selected folder
- [ ] Show folder contents preview
- [ ] Handle permission denied
- [ ] Validate folder accessibility
- [ ] Show storage info (available space)
- [ ] Confirm folder selection

### DriveFolderSelectScreen (Browse Drive Folders)
- [ ] Create DriveFolderSelectScreen composable
- [ ] Create DriveFolderSelectViewModel
- [ ] Display Drive folder hierarchy
- [ ] Implement folder navigation (breadcrumbs)
- [ ] Show folder contents (subfolders only)
- [ ] Add "Create New Folder" option
- [ ] Handle loading state
- [ ] Handle empty folder state
- [ ] Confirm folder selection
- [ ] Support search/filter folders

### ConflictResolutionScreen
- [ ] Create ConflictResolutionScreen composable
- [ ] Create ConflictResolutionViewModel
- [ ] List all conflicted files
- [ ] Display file details for each conflict:
  - [ ] Local version info (modified time, size)
  - [ ] Remote version info (modified time, size)
- [ ] Resolution options per file:
  - [ ] Keep Local
  - [ ] Keep Remote
  - [ ] Keep Both
- [ ] "Apply to All" bulk actions
- [ ] Preview file differences (if text)
- [ ] Confirm resolution and sync

### Shared Components

#### SyncStatusCard
- [ ] Create SyncStatusCard composable
- [ ] Display status icon (animated when syncing)
- [ ] Show status text
- [ ] Show last sync timestamp
- [ ] Color-coded by status

#### FolderInfoCard
- [ ] Create FolderInfoCard composable
- [ ] Display folder icon
- [ ] Show folder path/name
- [ ] Show file count and total size
- [ ] Add "Change" button

#### SyncProgressIndicator
- [ ] Create SyncProgressIndicator composable
- [ ] Linear progress bar
- [ ] Files processed / total files text
- [ ] Current file name being synced
- [ ] Cancel sync button

#### FileListItem
- [ ] Create FileListItem composable
- [ ] File type icon
- [ ] File name
- [ ] Size and modified date
- [ ] Sync status indicator

#### ConfirmationDialog
- [ ] Create reusable ConfirmationDialog
- [ ] Title, message, confirm/cancel buttons
- [ ] Destructive action styling

#### ErrorSnackbar
- [ ] Create error snackbar handler
- [ ] Retry action support
- [ ] Auto-dismiss with timer

### Navigation Setup
- [ ] Add Navigation Compose dependency
- [ ] Create NavHost with destinations
- [ ] Define navigation routes:
  - [ ] home
  - [ ] settings
  - [ ] folder-select/local
  - [ ] folder-select/drive
  - [ ] conflicts
  - [ ] sign-in
- [ ] Implement navigation actions
- [ ] Handle deep links (optional)
- [ ] Add bottom navigation or drawer (if needed)
- [ ] Handle back stack properly

## Acceptance Criteria
- [ ] All screens render correctly
- [ ] Material 3 theme applied consistently
- [ ] Dark mode works correctly
- [ ] Navigation flows work smoothly
- [ ] Loading states provide feedback
- [ ] Error states are informative
- [ ] Accessibility labels are present
- [ ] UI is responsive to different screen sizes
- [ ] Animations are smooth (60fps)

## Dependencies on Other Components
| Component | Dependency Type | Description |
|-----------|-----------------|-------------|
| Sync Engine | Required | Sync status and operations |
| Local Storage | Required | Folder and file information |
| Authentication | Required | Sign-in/out, user info |
| Drive API | Required | Drive folder browsing |
| App Infrastructure | Required | ViewModels with Hilt |

## Estimated Effort
| Task Category | Hours |
|---------------|-------|
| Theme Setup | 3 |
| HomeScreen | 5 |
| SettingsScreen | 4 |
| FolderSelectScreen | 3 |
| DriveFolderSelectScreen | 5 |
| ConflictResolutionScreen | 5 |
| Shared Components | 6 |
| Navigation Setup | 3 |
| Testing & Polish | 6 |
| **Total** | **40** |

## Technical Notes
- Use `collectAsStateWithLifecycle()` for Flow collection
- Implement `rememberSaveable` for screen state preservation
- Use `LaunchedEffect` for one-time UI events
- Consider using Accompanist libraries for system UI
- Implement proper keyboard handling
- Test with TalkBack for accessibility
- Use Coil for async image loading if needed

## Screen Flow Diagram
```
[Sign-In] → [Home] ←→ [Settings]
              ↓
    [Folder Select] (Local/Drive)
              ↓
         [Conflicts] (if any)
```
