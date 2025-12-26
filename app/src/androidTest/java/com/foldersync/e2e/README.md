# FolderSync E2E Tests

End-to-end instrumented tests that run on a physical Android device with real Google Drive authentication and sync operations.

## Prerequisites

1. **Physical Android device** connected via USB with:
   - Android 8.0 (API 26) or higher
   - USB debugging enabled
   - Google account signed in on device
   - Internet connectivity

2. **Development environment** with:
   - Android SDK installed
   - `adb` command available in PATH
   - Gradle wrapper (`./gradlew`) available

## Test Structure

### SimpleE2ETest (Recommended for first run)
A simplified test that doesn't require Hilt injection - easier to run and debug.

**Tests included:**
1. `test01_appLaunches` - Verifies app launches and shows UI
2. `test02_connectToGoogleDrive` - Signs in to Google (with user interaction)
3. `test03_selectFolders` - Selects local and Drive folders (with user interaction)
4. `test04_forceSync` - Triggers sync and waits for completion
5. `test05_verifyFinalState` - Verifies app is in correct state after sync

### GoogleDriveE2ETest
Full Hilt-integrated test with more comprehensive setup.

## Running the Tests

### Option 1: Using the script (recommended)

```bash
cd /Users/suressan/github_manager/repos/foldersync
./run_e2e_tests.sh
```

### Option 2: Using Gradle

```bash
# Run all instrumented tests
./gradlew connectedAndroidTest

# Run only SimpleE2ETest
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.foldersync.e2e.SimpleE2ETest

# Run only GoogleDriveE2ETest
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.foldersync.e2e.GoogleDriveE2ETest
```

### Option 3: Using adb directly

```bash
# Build and install first
./gradlew installDebug installDebugAndroidTest

# Run SimpleE2ETest
adb shell am instrument -w \
    -e class "com.foldersync.e2e.SimpleE2ETest" \
    com.foldersync.test/androidx.test.runner.AndroidJUnitRunner

# Run GoogleDriveE2ETest with Hilt runner
adb shell am instrument -w \
    -e class "com.foldersync.e2e.GoogleDriveE2ETest" \
    com.foldersync.test/com.foldersync.HiltTestRunner
```

## User Interaction Required

These tests require manual user intervention at certain points:

1. **Google Sign-In**: When the OAuth dialog appears, select your Google account and grant permissions
2. **Local Folder Selection**: When the folder picker appears, navigate to and select a folder
3. **Drive Folder Selection**: When the Drive folder browser appears, select a destination folder

The tests will print messages to the console indicating when user action is needed and will wait for the action to complete before continuing.

## Timeouts

Configured in `E2ETestConfig.kt`:
- UI element timeout: 30 seconds
- OAuth flow timeout: 120 seconds
- Sync operation timeout: 300 seconds

## Screenshots

The tests automatically capture screenshots at key points. Screenshots are saved to the app's cache directory and can be retrieved with:

```bash
adb shell "ls -la /data/data/com.foldersync/cache/screenshots/"
adb pull /data/data/com.foldersync/cache/screenshots/ ./test_screenshots/
```

## Troubleshooting

### App not found
```
Could not find launch intent for com.foldersync
```
**Solution:** Install the app first with `./gradlew installDebug`

### No devices connected
```
No Android devices connected!
```
**Solution:** Connect a device and enable USB debugging

### OAuth flow times out
**Solution:** Complete the sign-in flow faster, or increase `OAUTH_TIMEOUT_MS` in `E2ETestConfig.kt`

### Test runner not found
```
Unable to find instrumentation target
```
**Solution:** Install the test APK with `./gradlew installDebugAndroidTest`

## Test Reports

After running tests via Gradle, HTML reports are available at:
```
app/build/reports/androidTests/connected/index.html
```
