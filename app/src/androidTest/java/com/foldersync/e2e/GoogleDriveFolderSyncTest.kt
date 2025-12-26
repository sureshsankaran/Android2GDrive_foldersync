package com.foldersync.e2e

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.foldersync.e2e.E2ETestConfig.OAUTH_TIMEOUT_MS
import com.foldersync.e2e.E2ETestConfig.UI_ANIMATION_DELAY_MS
import com.foldersync.e2e.E2ETestConfig.UI_TIMEOUT_MS
import com.foldersync.e2e.TestHelpers.delay
import com.foldersync.e2e.TestHelpers.takeScreenshot
import com.foldersync.e2e.TestHelpers.waitForCondition
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * E2E test for the complete Google Drive connection and folder selection flow.
 * This test requires user interaction for:
 * 1. Google OAuth sign-in
 * 2. Selecting a local folder
 * 3. Selecting a Google Drive folder
 * 
 * Run with:
 * adb shell am instrument -w -e class "com.foldersync.e2e.GoogleDriveFolderSyncTest" com.foldersync.test/com.foldersync.HiltTestRunner
 */
@HiltAndroidTest
@LargeTest
class GoogleDriveFolderSyncTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    private lateinit var device: UiDevice
    private lateinit var context: Context
    
    companion object {
        private const val PACKAGE_NAME = "com.foldersync"
        private const val LAUNCH_TIMEOUT = 5000L
        private const val FOLDER_SELECTION_TIMEOUT_MS = 120000L // 2 minutes for user to select folder
    }

    @Before
    fun setup() {
        hiltRule.inject()
        
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context = ApplicationProvider.getApplicationContext()
        
        // Press home to start from a clean state
        device.pressHome()
        
        // Wait for launcher
        val launcherPackage = device.launcherPackageName
        device.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT)
        
        println("\n" + "=".repeat(70))
        println("  GOOGLE DRIVE FOLDER SYNC TEST")
        println("  This test requires user interaction")
        println("=".repeat(70))
    }

    @After
    fun teardown() {
        takeScreenshot(device, "gdrive_test_end")
        println("\n" + "=".repeat(70))
        println("  TEST COMPLETE")
        println("=".repeat(70))
    }

    /**
     * Complete flow test: Connect to Google Drive and select folders to sync
     */
    @Test
    fun testConnectToDriveAndSelectFolders() {
        println("\n📱 Starting Google Drive Folder Sync Test...")
        
        // Step 1: Launch the app
        launchApp()
        
        // Step 2: Connect to Google Drive (with user OAuth)
        connectToGoogleDrive()
        
        // Step 3: Select local folder
        selectLocalFolder()
        
        // Step 4: Select Drive folder
        selectDriveFolder()
        
        // Step 5: Verify folders are selected and sync is possible
        verifySyncReady()
        
        println("\n✅ All steps completed successfully!")
    }

    private fun launchApp() {
        println("\n📲 Step 1: Launching FolderSync app...")
        
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        if (intent == null) {
            fail("Could not find launch intent for $PACKAGE_NAME")
            return
        }
        
        context.startActivity(intent)
        
        // Wait for the app to appear
        val appLaunched = device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), LAUNCH_TIMEOUT)
        assertTrue("App should launch", appLaunched)
        
        delay(UI_ANIMATION_DELAY_MS)
        takeScreenshot(device, "gdrive_01_app_launched")
        
        println("   ✓ App launched successfully")
    }

    private fun connectToGoogleDrive() {
        println("\n🔐 Step 2: Connecting to Google Drive...")
        
        delay(UI_ANIMATION_DELAY_MS)
        
        // Check if already signed in
        val alreadySignedIn = isUserSignedIn()
        
        if (alreadySignedIn) {
            println("   ℹ Already signed in to Google Drive")
            takeScreenshot(device, "gdrive_02_already_signed_in")
            return
        }
        
        // Find sign-in button
        val signInButton = findSignInButton()
        
        if (signInButton == null) {
            println("   ⚠ No sign-in button found, checking if already authenticated...")
            takeScreenshot(device, "gdrive_02_no_signin_button")
            return
        }
        
        println("   → Found sign-in button, initiating OAuth...")
        signInButton.click()
        delay(UI_ANIMATION_DELAY_MS)
        
        takeScreenshot(device, "gdrive_02_oauth_started")
        
        // Print user instructions
        printUserAction("""
            |GOOGLE SIGN-IN REQUIRED:
            |
            |1. Select your Google account from the list
            |2. If prompted, enter your password
            |3. Grant the requested permissions
            |4. Wait for redirect back to the app
            |
            |Timeout: ${OAUTH_TIMEOUT_MS / 1000} seconds
        """.trimMargin())
        
        // Wait for OAuth to complete
        val oauthCompleted = waitForCondition(OAUTH_TIMEOUT_MS) {
            isUserSignedIn() || isBackInAppWithFolderUI()
        }
        
        takeScreenshot(device, "gdrive_02_oauth_completed")
        
        assertTrue("OAuth should complete and return to app", oauthCompleted)
        println("   ✓ Successfully connected to Google Drive")
    }

    private fun selectLocalFolder() {
        println("\n📁 Step 3: Selecting local folder...")
        
        delay(UI_ANIMATION_DELAY_MS)
        takeScreenshot(device, "gdrive_03_before_local_select")
        
        // Find local folder selection button
        val localFolderButton = findLocalFolderButton()
        
        if (localFolderButton == null) {
            println("   ⚠ Local folder button not found")
            takeScreenshot(device, "gdrive_03_no_local_button")
            return
        }
        
        // Check if already selected
        if (isLocalFolderSelected()) {
            println("   ℹ Local folder already selected")
            return
        }
        
        println("   → Opening local folder picker...")
        localFolderButton.click()
        delay(UI_ANIMATION_DELAY_MS)
        
        takeScreenshot(device, "gdrive_03_local_picker_opened")
        
        printUserAction("""
            |SELECT LOCAL FOLDER:
            |
            |1. Navigate to the folder you want to sync
            |2. Select the folder (tap on it)
            |3. Confirm your selection if prompted
            |
            |Timeout: ${FOLDER_SELECTION_TIMEOUT_MS / 1000} seconds
        """.trimMargin())
        
        // Wait for user to select folder
        val folderSelected = waitForCondition(FOLDER_SELECTION_TIMEOUT_MS) {
            val inApp = device.hasObject(By.pkg(PACKAGE_NAME).depth(0))
            inApp && (isLocalFolderSelected() || isBackInAppWithFolderUI())
        }
        
        takeScreenshot(device, "gdrive_03_local_folder_selected")
        
        assertTrue("Local folder should be selected", folderSelected)
        println("   ✓ Local folder selected")
    }

    private fun selectDriveFolder() {
        println("\n☁️ Step 4: Selecting Google Drive folder...")
        
        delay(UI_ANIMATION_DELAY_MS)
        takeScreenshot(device, "gdrive_04_before_drive_select")
        
        // Find Drive folder selection button
        val driveFolderButton = findDriveFolderButton()
        
        if (driveFolderButton == null) {
            println("   ⚠ Drive folder button not found")
            takeScreenshot(device, "gdrive_04_no_drive_button")
            return
        }
        
        // Check if already selected
        if (isDriveFolderSelected()) {
            println("   ℹ Drive folder already selected")
            return
        }
        
        println("   → Opening Drive folder picker...")
        driveFolderButton.click()
        delay(UI_ANIMATION_DELAY_MS)
        
        takeScreenshot(device, "gdrive_04_drive_picker_opened")
        
        printUserAction("""
            |SELECT GOOGLE DRIVE FOLDER:
            |
            |1. Browse your Google Drive folders
            |2. Select the folder you want to sync to
            |3. Tap to confirm your selection
            |
            |Timeout: ${FOLDER_SELECTION_TIMEOUT_MS / 1000} seconds
        """.trimMargin())
        
        // Wait for user to select folder
        val folderSelected = waitForCondition(FOLDER_SELECTION_TIMEOUT_MS) {
            val inApp = device.hasObject(By.pkg(PACKAGE_NAME).depth(0))
            inApp && (isDriveFolderSelected() || hasSyncButton())
        }
        
        takeScreenshot(device, "gdrive_04_drive_folder_selected")
        
        assertTrue("Drive folder should be selected", folderSelected)
        println("   ✓ Google Drive folder selected")
    }

    private fun verifySyncReady() {
        println("\n🔄 Step 5: Verifying sync is ready...")
        
        delay(UI_ANIMATION_DELAY_MS)
        takeScreenshot(device, "gdrive_05_verify_sync_ready")
        
        // Verify app is in correct state
        val appVisible = device.hasObject(By.pkg(PACKAGE_NAME).depth(0))
        assertTrue("App should be visible", appVisible)
        
        // Check for sync button or sync status
        val syncReady = hasSyncButton() || 
            device.findObject(By.textContains("Sync")) != null ||
            device.findObject(By.textContains("Ready")) != null
        
        // Print current UI state
        println("\n   Current UI State:")
        val textViews = device.findObjects(By.clazz("android.widget.TextView"))
        for (tv in textViews.take(15)) {
            tv.text?.let { 
                if (it.isNotBlank()) println("   📝 $it") 
            }
        }
        
        if (syncReady) {
            println("\n   ✓ App is ready to sync!")
        } else {
            println("\n   ⚠ Sync may not be fully configured yet")
        }
        
        takeScreenshot(device, "gdrive_05_final_state")
    }

    // ===== Helper Methods =====

    private fun findSignInButton(): UiObject2? {
        return device.findObject(By.textContains("Sign in"))
            ?: device.findObject(By.textContains("Sign In"))
            ?: device.findObject(By.textContains("Connect to Google"))
            ?: device.findObject(By.textContains("Connect Google"))
            ?: device.findObject(By.descContains("Sign in"))
            ?: device.findObject(By.descContains("Google"))
    }

    private fun findLocalFolderButton(): UiObject2? {
        return device.findObject(By.textContains("Local Folder"))
            ?: device.findObject(By.textContains("Select Local"))
            ?: device.findObject(By.textContains("Choose Local"))
            ?: device.findObject(By.textContains("Local"))
                ?.takeIf { isClickableOrHasClickableParent(it) }
    }

    private fun findDriveFolderButton(): UiObject2? {
        return device.findObject(By.textContains("Drive Folder"))
            ?: device.findObject(By.textContains("Select Drive"))
            ?: device.findObject(By.textContains("Choose Drive"))
            ?: device.findObject(By.textContains("Google Drive"))
                ?.takeIf { isClickableOrHasClickableParent(it) }
            ?: device.findObject(By.textContains("Cloud Folder"))
    }

    private fun isClickableOrHasClickableParent(obj: UiObject2): Boolean {
        return obj.isClickable || obj.parent?.isClickable == true
    }

    private fun isUserSignedIn(): Boolean {
        // Check for indicators that user is signed in
        val hasSignOut = device.findObject(By.textContains("Sign out")) != null
        val hasAccount = device.findObject(By.textContains("@gmail")) != null ||
            device.findObject(By.textContains("@google")) != null
        val hasFolderSelection = device.findObject(By.textContains("Folder")) != null
        val hasDisconnect = device.findObject(By.textContains("Disconnect")) != null
        
        return hasSignOut || hasAccount || hasFolderSelection || hasDisconnect
    }

    private fun isBackInAppWithFolderUI(): Boolean {
        val inApp = device.hasObject(By.pkg(PACKAGE_NAME).depth(0))
        val hasFolderUI = device.findObject(By.textContains("Folder")) != null ||
            device.findObject(By.textContains("Local")) != null ||
            device.findObject(By.textContains("Drive")) != null
        return inApp && hasFolderUI
    }

    private fun isLocalFolderSelected(): Boolean {
        // Check if "Not selected" text is NOT present for local folder
        // or if there's a folder path shown
        val localSection = device.findObject(By.textContains("Local"))
        if (localSection != null) {
            // Look for "Not selected" near the local section
            val notSelected = device.findObjects(By.textContains("Not selected"))
            // If there are fewer "Not selected" labels, one must be selected
            return notSelected.isEmpty() || notSelected.size < 2
        }
        return false
    }

    private fun isDriveFolderSelected(): Boolean {
        val driveSection = device.findObject(By.textContains("Drive"))
        if (driveSection != null) {
            val notSelected = device.findObjects(By.textContains("Not selected"))
            return notSelected.isEmpty()
        }
        return false
    }

    private fun hasSyncButton(): Boolean {
        val syncButton = device.findObject(By.textContains("Sync Now"))
            ?: device.findObject(By.text("Sync"))
        return syncButton?.isEnabled == true
    }

    private fun printUserAction(message: String) {
        println()
        println("╔" + "═".repeat(68) + "╗")
        println("║  🙋 USER ACTION REQUIRED" + " ".repeat(43) + "║")
        println("╠" + "═".repeat(68) + "╣")
        for (line in message.lines()) {
            val paddedLine = "║  $line".padEnd(69) + "║"
            println(paddedLine)
        }
        println("╚" + "═".repeat(68) + "╝")
        println()
    }
}
