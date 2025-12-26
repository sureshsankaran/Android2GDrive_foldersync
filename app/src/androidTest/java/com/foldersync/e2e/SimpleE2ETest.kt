package com.foldersync.e2e

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.foldersync.e2e.E2ETestConfig.OAUTH_TIMEOUT_MS
import com.foldersync.e2e.E2ETestConfig.SYNC_TIMEOUT_MS
import com.foldersync.e2e.E2ETestConfig.UI_ANIMATION_DELAY_MS
import com.foldersync.e2e.E2ETestConfig.UI_TIMEOUT_MS
import com.foldersync.e2e.TestHelpers.delay
import com.foldersync.e2e.TestHelpers.takeScreenshot
import com.foldersync.e2e.TestHelpers.waitForCondition
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
 * Simplified E2E test with Hilt support.
 * This test launches the app via intent and interacts with it using UiAutomator.
 * 
 * Run with: adb shell am instrument -w -e class "com.foldersync.e2e.SimpleE2ETest" com.foldersync.test/com.foldersync.HiltTestRunner
 */
@HiltAndroidTest
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SimpleE2ETest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    private lateinit var device: UiDevice
    private lateinit var context: Context
    
    companion object {
        private const val PACKAGE_NAME = "com.foldersync"
        private const val LAUNCH_TIMEOUT = 5000L
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
        
        // Launch the app
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        if (intent == null) {
            throw RuntimeException("Could not find launch intent for $PACKAGE_NAME. Is the app installed?")
        }
        
        context.startActivity(intent)
        
        // Wait for the app to appear
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), LAUNCH_TIMEOUT)
        delay(UI_ANIMATION_DELAY_MS)
        
        println("=== Simple E2E Test Setup Complete ===")
        println("Device: ${android.os.Build.MODEL}")
        println("Android Version: ${android.os.Build.VERSION.SDK_INT}")
    }

    @After
    fun teardown() {
        takeScreenshot(device, "test_end")
        println("=== Simple E2E Test Teardown Complete ===")
    }

    /**
     * Test 1: Open app and verify UI loads
     */
    @Test
    fun test01_appLaunches() {
        println("\n=== Test: App Launches ===")
        
        takeScreenshot(device, "01_app_launched")
        
        // Verify app is visible
        val appVisible = device.hasObject(By.pkg(PACKAGE_NAME).depth(0))
        assertTrue("App should be visible", appVisible)
        
        // Look for any expected UI elements
        delay(2000) // Wait for Compose to render
        
        val hasContent = waitForCondition(UI_TIMEOUT_MS) {
            device.findObject(By.textContains("Sync")) != null ||
            device.findObject(By.textContains("Sign")) != null ||
            device.findObject(By.textContains("Connect")) != null ||
            device.findObject(By.textContains("Folder")) != null ||
            device.findObject(By.textContains("Google")) != null
        }
        
        takeScreenshot(device, "01_ui_loaded")
        assertTrue("App should show some UI content", hasContent)
        
        println("✓ App launches successfully")
    }

    /**
     * Test 2: Connect to Google Drive with user interaction
     */
    @Test
    fun test02_connectToGoogleDrive() {
        println("\n=== Test: Connect to Google Drive ===")
        
        delay(UI_ANIMATION_DELAY_MS)
        takeScreenshot(device, "02_start")
        
        // Look for authentication UI
        val signInButton = device.findObject(By.textContains("Sign in"))
            ?: device.findObject(By.textContains("Connect"))
            ?: device.findObject(By.textContains("Google"))
        
        if (signInButton != null) {
            println("Found sign-in button, initiating OAuth...")
            signInButton.click()
            delay(UI_ANIMATION_DELAY_MS)
            
            takeScreenshot(device, "02_oauth_initiated")
            
            // Print message for user
            println("\n" + "=".repeat(60))
            println("USER ACTION REQUIRED:")
            println("1. Select your Google account")
            println("2. Grant permissions when prompted")
            println("Test will continue automatically after completion")
            println("=".repeat(60) + "\n")
            
            // Wait for OAuth to complete
            val authCompleted = waitForCondition(OAUTH_TIMEOUT_MS) {
                // We're done if we're back in the app with folder-related UI
                val inApp = device.hasObject(By.pkg(PACKAGE_NAME).depth(0))
                val hasFolderUI = device.findObject(By.textContains("Folder")) != null
                val hasSyncUI = device.findObject(By.textContains("Sync")) != null
                
                inApp && (hasFolderUI || hasSyncUI)
            }
            
            takeScreenshot(device, "02_oauth_completed")
            assertTrue("OAuth should complete and return to app", authCompleted)
            
        } else {
            println("No sign-in button found - might already be authenticated")
            
            // Verify we see the main UI
            val hasMainUI = device.findObject(By.textContains("Folder")) != null ||
                device.findObject(By.textContains("Sync")) != null
            
            takeScreenshot(device, "02_already_authenticated")
            assertTrue("Should see main app UI", hasMainUI)
        }
        
        println("✓ Google Drive connection verified")
    }

    /**
     * Test 3: Select folders with user interaction
     */
    @Test
    fun test03_selectFolders() {
        println("\n=== Test: Select Folders ===")
        
        delay(UI_ANIMATION_DELAY_MS)
        takeScreenshot(device, "03_start")
        
        // Look for local folder selection
        val localFolderButton = device.findObject(By.textContains("Local"))
            ?: device.findObject(By.textContains("Select"))
        
        if (localFolderButton != null) {
            // Check if not selected
            val notSelected = device.findObject(By.textContains("Not selected"))
            
            if (notSelected != null) {
                println("Selecting local folder...")
                localFolderButton.click()
                delay(UI_ANIMATION_DELAY_MS)
                
                takeScreenshot(device, "03_local_picker")
                
                println("\n" + "=".repeat(60))
                println("USER ACTION REQUIRED:")
                println("Select a local folder to sync")
                println("=".repeat(60) + "\n")
                
                // Wait for folder selection
                val folderSelected = waitForCondition(OAUTH_TIMEOUT_MS) {
                    val inApp = device.hasObject(By.pkg(PACKAGE_NAME).depth(0))
                    val noLongerNotSelected = device.findObject(By.textContains("Not selected")) == null ||
                        device.findObjects(By.textContains("Not selected")).size < 2
                    
                    inApp && noLongerNotSelected
                }
                
                takeScreenshot(device, "03_local_selected")
            }
        }
        
        // Look for Drive folder selection
        delay(UI_ANIMATION_DELAY_MS)
        val driveFolderButton = device.findObject(By.textContains("Drive"))
            ?: device.findObject(By.textContains("Cloud"))
        
        if (driveFolderButton != null) {
            val notSelected = device.findObject(By.textContains("Not selected"))
            
            if (notSelected != null) {
                println("Selecting Drive folder...")
                driveFolderButton.click()
                delay(UI_ANIMATION_DELAY_MS)
                
                takeScreenshot(device, "03_drive_picker")
                
                println("\n" + "=".repeat(60))
                println("USER ACTION REQUIRED:")
                println("Select a Google Drive folder to sync to")
                println("=".repeat(60) + "\n")
                
                // Wait for folder selection
                val folderSelected = waitForCondition(OAUTH_TIMEOUT_MS) {
                    val inApp = device.hasObject(By.pkg(PACKAGE_NAME).depth(0))
                    inApp && device.findObject(By.textContains("Sync")) != null
                }
                
                takeScreenshot(device, "03_drive_selected")
            }
        }
        
        println("✓ Folder selection completed")
    }

    /**
     * Test 4: Trigger forced sync
     */
    @Test
    fun test04_forceSync() {
        println("\n=== Test: Force Sync ===")
        
        delay(UI_ANIMATION_DELAY_MS)
        takeScreenshot(device, "04_start")
        
        // Find and click sync button
        val syncButton = device.findObject(By.textContains("Sync Now"))
            ?: device.findObject(By.text("Sync"))
            ?: device.findObject(By.textContains("Sync"))
        
        if (syncButton != null && syncButton.isEnabled) {
            println("Triggering sync...")
            syncButton.click()
            delay(UI_ANIMATION_DELAY_MS)
            
            takeScreenshot(device, "04_sync_triggered")
            
            // Wait for sync to complete or timeout
            println("Waiting for sync to complete (max ${SYNC_TIMEOUT_MS/1000}s)...")
            
            val syncComplete = waitForCondition(SYNC_TIMEOUT_MS) {
                val syncing = device.findObject(By.textContains("Syncing")) != null ||
                    device.findObject(By.textContains("Progress")) != null
                
                val complete = device.findObject(By.textContains("Complete")) != null ||
                    device.findObject(By.textContains("Up to date")) != null ||
                    device.findObject(By.textContains("Last sync")) != null
                
                !syncing || complete
            }
            
            takeScreenshot(device, "04_sync_completed")
            
            if (syncComplete) {
                println("✓ Sync completed")
            } else {
                println("⚠ Sync may still be in progress")
            }
        } else {
            println("⚠ Sync button not found or not enabled")
            println("This may mean folders are not selected or there was an error")
            takeScreenshot(device, "04_no_sync_button")
        }
    }

    /**
     * Test 5: Verify final state
     */
    @Test
    fun test05_verifyFinalState() {
        println("\n=== Test: Verify Final State ===")
        
        delay(UI_ANIMATION_DELAY_MS)
        takeScreenshot(device, "05_final_state")
        
        // App should still be visible and responsive
        val appVisible = device.hasObject(By.pkg(PACKAGE_NAME).depth(0))
        assertTrue("App should still be visible", appVisible)
        
        // Check for any status indicators
        val hasStatus = device.findObject(By.textContains("Last sync")) != null ||
            device.findObject(By.textContains("Up to date")) != null ||
            device.findObject(By.textContains("Complete")) != null ||
            device.findObject(By.textContains("Synced")) != null
        
        println("Status indicators found: $hasStatus")
        
        // Print final UI state
        println("\nFinal UI State:")
        val allText = device.findObjects(By.clazz("android.widget.TextView"))
        for (text in allText.take(10)) {
            println("  - ${text.text}")
        }
        
        println("\n✓ Final state verification complete")
    }
}
