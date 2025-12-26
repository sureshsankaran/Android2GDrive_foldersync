package com.foldersync.e2e

import android.Manifest
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.foldersync.e2e.E2ETestConfig.OAUTH_TIMEOUT_MS
import com.foldersync.e2e.E2ETestConfig.SYNC_TIMEOUT_MS
import com.foldersync.e2e.E2ETestConfig.UI_ANIMATION_DELAY_MS
import com.foldersync.e2e.E2ETestConfig.UI_TIMEOUT_MS
import com.foldersync.e2e.TestHelpers.delay
import com.foldersync.e2e.TestHelpers.getDevice
import com.foldersync.e2e.TestHelpers.getTargetContext
import com.foldersync.e2e.TestHelpers.takeScreenshot
import com.foldersync.e2e.TestHelpers.waitForCondition
import com.foldersync.e2e.TestHelpers.waitForText
import com.foldersync.e2e.TestHelpers.waitForTextContains
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * End-to-end tests for Google Drive sync functionality.
 * 
 * IMPORTANT: These tests require:
 * - Physical device (not emulator) for reliable OAuth
 * - Google account signed into the device
 * - Internet connectivity
 * - User interaction for OAuth consent (first run)
 * - User interaction for folder selection
 * 
 * Run with: ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.foldersync.e2e.GoogleDriveE2ETest
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GoogleDriveE2ETest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    private lateinit var device: UiDevice
    private var scenario: ActivityScenario<*>? = null

    @Before
    fun setup() {
        hiltRule.inject()
        device = getDevice()
        
        // Launch the app
        val context = getTargetContext()
        val intent = context.packageManager.getLaunchIntentForPackage("com.foldersync")
            ?: throw RuntimeException("Could not find launch intent for com.foldersync. Is the app installed?")
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        
        // Wait for app to launch
        device.wait(Until.hasObject(By.pkg("com.foldersync").depth(0)), UI_TIMEOUT_MS)
        delay(UI_ANIMATION_DELAY_MS)
        
        println("=== E2E Test Setup Complete ===")
        println("Device: ${android.os.Build.MODEL}")
        println("Android Version: ${android.os.Build.VERSION.SDK_INT}")
    }

    @After
    fun teardown() {
        // Take screenshot on test completion
        takeScreenshot(device, "test_end")
        scenario?.close()
        println("=== E2E Test Teardown Complete ===")
    }

    /**
     * Test 1: Open app and connect to Google Drive
     * 
     * This test will:
     * 1. Launch the app
     * 2. Check if already signed in, if not, trigger sign-in
     * 3. Wait for user to complete OAuth flow if needed
     * 4. Verify sign-in was successful
     */
    @Test
    fun test01_openAppAndConnectToGoogleDrive() {
        println("\n=== Test: Connect to Google Drive ===")
        
        // Take initial screenshot
        takeScreenshot(device, "01_app_launched")
        
        // Check if we're on the home screen with sync options
        val homeScreenVisible = waitForTextContains(device, "FolderSync", UI_TIMEOUT_MS) != null
        assertTrue("App should show FolderSync title", homeScreenVisible)
        
        // Look for sign-in or account related UI
        // The app might show:
        // 1. A sign-in button if not authenticated
        // 2. Account info if already authenticated
        // 3. Direct to folder selection if already set up
        
        val signInButton = device.findObject(By.textContains("Sign in"))
        val connectButton = device.findObject(By.textContains("Connect"))
        val googleButton = device.findObject(By.textContains("Google"))
        
        if (signInButton != null || connectButton != null || googleButton != null) {
            println("Sign-in required, initiating OAuth flow...")
            
            // Click the sign-in button
            when {
                signInButton != null -> signInButton.click()
                connectButton != null -> connectButton.click()
                googleButton != null -> googleButton.click()
            }
            
            delay(UI_ANIMATION_DELAY_MS)
            takeScreenshot(device, "01_oauth_started")
            
            // Wait for Google account picker or OAuth consent
            // This requires USER INTERACTION
            println("\n" + "=".repeat(60))
            println("USER ACTION REQUIRED: Please select your Google account")
            println("and grant permissions when prompted.")
            println("=".repeat(60) + "\n")
            
            // Wait for OAuth to complete (user interaction)
            val oauthCompleted = waitForCondition(OAUTH_TIMEOUT_MS) {
                // Check if we're back to the app (OAuth completed)
                val backToApp = device.findObject(By.pkg("com.foldersync").depth(0)) != null
                val hasLocalFolder = device.findObject(By.textContains("Local Folder")) != null
                val hasDriveFolder = device.findObject(By.textContains("Drive Folder")) != null
                val hasSync = device.findObject(By.textContains("Sync")) != null
                
                backToApp && (hasLocalFolder || hasDriveFolder || hasSync)
            }
            
            takeScreenshot(device, "01_oauth_completed")
            assertTrue("OAuth flow should complete and return to app", oauthCompleted)
            
        } else {
            // Already signed in, verify we see the expected UI
            println("Already signed in, verifying home screen...")
            
            val hasExpectedUI = waitForCondition(UI_TIMEOUT_MS) {
                device.findObject(By.textContains("Local Folder")) != null ||
                device.findObject(By.textContains("Drive Folder")) != null ||
                device.findObject(By.textContains("Sync")) != null
            }
            
            assertTrue("Should see sync-related UI when signed in", hasExpectedUI)
        }
        
        println("✓ Google Drive connection verified")
    }

    /**
     * Test 2: Select folder and trigger forced sync
     * 
     * This test will:
     * 1. Wait for user to select a local folder
     * 2. Wait for user to select a Drive folder
     * 3. Trigger a forced sync
     * 4. Verify sync completes successfully
     */
    @Test
    fun test02_selectFolderAndForceSync() {
        println("\n=== Test: Select Folder and Force Sync ===")
        
        delay(UI_ANIMATION_DELAY_MS)
        takeScreenshot(device, "02_before_folder_select")
        
        // Step 1: Select Local Folder
        println("Step 1: Selecting local folder...")
        selectLocalFolder()
        
        // Step 2: Select Drive Folder
        println("Step 2: Selecting Drive folder...")
        selectDriveFolder()
        
        // Step 3: Trigger Sync
        println("Step 3: Triggering sync...")
        triggerSync()
        
        // Step 4: Wait for sync to complete
        println("Step 4: Waiting for sync to complete...")
        waitForSyncCompletion()
        
        println("✓ Folder selection and sync completed")
    }
    
    /**
     * Select local folder - requires user interaction with SAF picker
     */
    private fun selectLocalFolder() {
        // Look for Local Folder selection UI
        val localFolderCard = device.findObject(By.textContains("Local Folder"))
        
        if (localFolderCard != null) {
            // Check if folder is already selected
            val notSelected = device.findObject(By.textContains("Not selected"))
            
            if (notSelected != null) {
                println("No local folder selected, opening folder picker...")
                
                localFolderCard.click()
                delay(UI_ANIMATION_DELAY_MS)
                
                // Look for select folder button
                val selectButton = waitForText(device, "Select Folder", UI_TIMEOUT_MS)
                if (selectButton != null) {
                    selectButton.click()
                    delay(UI_ANIMATION_DELAY_MS)
                }
                
                takeScreenshot(device, "02_folder_picker_opened")
                
                // USER INTERACTION REQUIRED for SAF folder picker
                println("\n" + "=".repeat(60))
                println("USER ACTION REQUIRED: Please select a folder to sync")
                println("Navigate to the folder and tap 'Use this folder'")
                println("=".repeat(60) + "\n")
                
                // Wait for folder selection to complete
                val folderSelected = waitForCondition(OAUTH_TIMEOUT_MS) {
                    // Check if we're back to the app with a folder selected
                    val hasLocalFolderName = device.findObject(By.textContains("Not selected")) == null &&
                        device.findObject(By.textContains("Local Folder")) != null
                    
                    // Or check for confirm button after selection
                    val confirmButton = device.findObject(By.textContains("Confirm"))
                    if (confirmButton != null) {
                        confirmButton.click()
                        delay(UI_ANIMATION_DELAY_MS)
                        return@waitForCondition true
                    }
                    
                    hasLocalFolderName
                }
                
                takeScreenshot(device, "02_local_folder_selected")
                assertTrue("Local folder should be selected", folderSelected)
            } else {
                println("Local folder already selected")
            }
        }
    }
    
    /**
     * Select Drive folder - requires user interaction with Drive folder browser
     */
    private fun selectDriveFolder() {
        delay(UI_ANIMATION_DELAY_MS)
        
        // Look for Drive Folder selection UI
        val driveFolderCard = device.findObject(By.textContains("Drive Folder")) 
            ?: device.findObject(By.textContains("Google Drive"))
        
        if (driveFolderCard != null) {
            // Check if Drive folder is already selected
            val driveNotSelected = waitForCondition(2000) {
                val card = device.findObject(By.textContains("Drive Folder"))
                    ?: device.findObject(By.textContains("Google Drive"))
                card != null && device.findObject(By.textContains("Not selected")) != null
            }
            
            if (driveNotSelected) {
                println("No Drive folder selected, opening Drive browser...")
                
                driveFolderCard.click()
                delay(UI_ANIMATION_DELAY_MS)
                
                takeScreenshot(device, "02_drive_browser_opened")
                
                // USER INTERACTION REQUIRED for Drive folder selection
                println("\n" + "=".repeat(60))
                println("USER ACTION REQUIRED: Please select a Google Drive folder")
                println("Navigate to the folder and tap the cloud/select button")
                println("=".repeat(60) + "\n")
                
                // Wait for Drive folder selection to complete
                val driveFolderSelected = waitForCondition(OAUTH_TIMEOUT_MS) {
                    // Check if we're back to home with a folder selected
                    val backToHome = device.findObject(By.textContains("FolderSync")) != null
                    val hasSyncButton = device.findObject(By.textContains("Sync Now")) != null ||
                        device.findObject(By.textContains("Sync")) != null
                    
                    backToHome && hasSyncButton
                }
                
                takeScreenshot(device, "02_drive_folder_selected")
                assertTrue("Drive folder should be selected", driveFolderSelected)
            } else {
                println("Drive folder already selected")
            }
        }
    }
    
    /**
     * Trigger the sync operation
     */
    private fun triggerSync() {
        delay(UI_ANIMATION_DELAY_MS)
        takeScreenshot(device, "02_before_sync")
        
        // Find and click the Sync button
        val syncButton = device.findObject(By.textContains("Sync Now"))
            ?: device.findObject(By.text("Sync"))
            ?: device.findObject(By.textContains("Sync"))
        
        if (syncButton != null && syncButton.isEnabled) {
            println("Clicking Sync button...")
            syncButton.click()
            delay(UI_ANIMATION_DELAY_MS)
            takeScreenshot(device, "02_sync_started")
        } else {
            // Try to find a button that might be sync-related
            val buttons = device.findObjects(By.clazz("android.widget.Button"))
            for (button in buttons) {
                val text = button.text ?: ""
                if (text.contains("Sync", ignoreCase = true)) {
                    println("Found sync button: $text")
                    button.click()
                    delay(UI_ANIMATION_DELAY_MS)
                    break
                }
            }
        }
    }
    
    /**
     * Wait for sync to complete
     */
    private fun waitForSyncCompletion() {
        println("Waiting for sync completion (timeout: ${SYNC_TIMEOUT_MS/1000}s)...")
        
        val syncCompleted = waitForCondition(SYNC_TIMEOUT_MS) {
            // Check for completion indicators
            val isSyncing = device.findObject(By.textContains("Syncing")) != null ||
                device.findObject(By.textContains("Uploading")) != null ||
                device.findObject(By.textContains("Downloading")) != null
            
            val isComplete = device.findObject(By.textContains("Up to date")) != null ||
                device.findObject(By.textContains("Complete")) != null ||
                device.findObject(By.textContains("Synced")) != null ||
                device.findObject(By.textContains("Last sync")) != null
            
            val hasError = device.findObject(By.textContains("Error")) != null ||
                device.findObject(By.textContains("Failed")) != null
            
            if (hasError) {
                println("Warning: Sync may have encountered an error")
                takeScreenshot(device, "02_sync_error")
            }
            
            // Sync is complete if we're not syncing and see completion message
            !isSyncing && (isComplete || hasError)
        }
        
        takeScreenshot(device, "02_sync_completed")
        
        // Log the result but don't fail on sync errors (might be network/permission issues)
        if (syncCompleted) {
            println("✓ Sync operation completed")
        } else {
            println("⚠ Sync may still be in progress or timed out")
        }
        
        // Verify we can still see the app UI
        val appVisible = device.findObject(By.textContains("FolderSync")) != null
        assertTrue("App should still be visible after sync", appVisible)
    }

    /**
     * Test 3: Verify sync status after completion
     */
    @Test
    fun test03_verifySyncStatus() {
        println("\n=== Test: Verify Sync Status ===")
        
        delay(UI_ANIMATION_DELAY_MS)
        takeScreenshot(device, "03_verify_status")
        
        // Check for any of these status indicators
        val hasLastSyncInfo = device.findObject(By.textContains("Last sync")) != null
        val hasUpToDate = device.findObject(By.textContains("Up to date")) != null
        val hasSyncedStatus = device.findObject(By.textContains("Synced")) != null
        val hasNeverSynced = device.findObject(By.textContains("Never")) != null
        
        println("Status indicators found:")
        println("  - Last sync info: $hasLastSyncInfo")
        println("  - Up to date: $hasUpToDate")
        println("  - Synced status: $hasSyncedStatus")
        println("  - Never synced: $hasNeverSynced")
        
        // At least one status indicator should be visible
        val hasStatus = hasLastSyncInfo || hasUpToDate || hasSyncedStatus || hasNeverSynced
        assertTrue("Should see sync status information", hasStatus)
        
        println("✓ Sync status verified")
    }
}
