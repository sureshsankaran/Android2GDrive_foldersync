package com.foldersync.e2e

import android.content.Context
import android.os.Environment
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.File

/**
 * Helper utilities for E2E tests
 */
object TestHelpers {
    
    /**
     * Get the target context (app under test)
     */
    fun getTargetContext(): Context {
        return InstrumentationRegistry.getInstrumentation().targetContext
    }
    
    /**
     * Get the test context (test APK)
     */
    fun getTestContext(): Context {
        return InstrumentationRegistry.getInstrumentation().context
    }
    
    /**
     * Get UiDevice instance
     */
    fun getDevice(): UiDevice {
        return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }
    
    /**
     * Wait for an element with text to appear
     */
    fun waitForText(
        device: UiDevice,
        text: String,
        timeout: Long = E2ETestConfig.UI_TIMEOUT_MS
    ): UiObject2? {
        return device.wait(
            Until.findObject(By.text(text)),
            timeout
        )
    }
    
    /**
     * Wait for an element containing text to appear
     */
    fun waitForTextContains(
        device: UiDevice,
        text: String,
        timeout: Long = E2ETestConfig.UI_TIMEOUT_MS
    ): UiObject2? {
        return device.wait(
            Until.findObject(By.textContains(text)),
            timeout
        )
    }
    
    /**
     * Wait for an element with description to appear
     */
    fun waitForDescription(
        device: UiDevice,
        description: String,
        timeout: Long = E2ETestConfig.UI_TIMEOUT_MS
    ): UiObject2? {
        return device.wait(
            Until.findObject(By.desc(description)),
            timeout
        )
    }
    
    /**
     * Wait for an element with resource ID to appear
     */
    fun waitForResourceId(
        device: UiDevice,
        resourceId: String,
        timeout: Long = E2ETestConfig.UI_TIMEOUT_MS
    ): UiObject2? {
        return device.wait(
            Until.findObject(By.res(resourceId)),
            timeout
        )
    }
    
    /**
     * Click on element with text
     */
    fun clickText(device: UiDevice, text: String, timeout: Long = E2ETestConfig.UI_TIMEOUT_MS): Boolean {
        val element = waitForText(device, text, timeout)
        return element?.let {
            it.click()
            true
        } ?: false
    }
    
    /**
     * Click on element containing text
     */
    fun clickTextContains(device: UiDevice, text: String, timeout: Long = E2ETestConfig.UI_TIMEOUT_MS): Boolean {
        val element = waitForTextContains(device, text, timeout)
        return element?.let {
            it.click()
            true
        } ?: false
    }
    
    /**
     * Create test folder with sample files
     */
    fun createTestFolder(context: Context): File? {
        return try {
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val testFolder = File(documentsDir, E2ETestConfig.TEST_FOLDER_NAME)
            
            if (!testFolder.exists()) {
                testFolder.mkdirs()
            }
            
            // Create test files
            E2ETestConfig.TEST_FILES.forEach { fileName ->
                val file = File(testFolder, fileName)
                if (!file.exists()) {
                    if (fileName.endsWith(".txt")) {
                        file.writeText("Test content for $fileName\nCreated at: ${System.currentTimeMillis()}")
                    } else {
                        // Create a minimal PNG for image files
                        file.writeBytes(createMinimalPng())
                    }
                }
            }
            
            testFolder
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Clean up test folder
     */
    fun cleanupTestFolder() {
        try {
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val testFolder = File(documentsDir, E2ETestConfig.TEST_FOLDER_NAME)
            if (testFolder.exists()) {
                testFolder.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Create a minimal valid PNG file (1x1 transparent pixel)
     */
    private fun createMinimalPng(): ByteArray {
        return byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, // IHDR chunk
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, // 1x1 dimensions
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15.toByte(), 0xC4.toByte(), 0x89.toByte(), // Bit depth, color type, etc
            0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54, // IDAT chunk
            0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01, // Compressed data
            0x0D, 0x0A, 0x2D, 0xB4.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, // IEND chunk
            0xAE.toByte(), 0x42, 0x60, 0x82.toByte()
        )
    }
    
    /**
     * Take screenshot for debugging
     */
    fun takeScreenshot(device: UiDevice, name: String): Boolean {
        return try {
            val screenshotDir = File(getTargetContext().cacheDir, "screenshots")
            screenshotDir.mkdirs()
            val file = File(screenshotDir, "${name}_${System.currentTimeMillis()}.png")
            device.takeScreenshot(file)
            println("Screenshot saved: ${file.absolutePath}")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Wait for condition with polling
     */
    inline fun waitForCondition(
        timeout: Long = E2ETestConfig.UI_TIMEOUT_MS,
        pollInterval: Long = E2ETestConfig.POLL_INTERVAL_MS,
        condition: () -> Boolean
    ): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeout) {
            if (condition()) {
                return true
            }
            Thread.sleep(pollInterval)
        }
        return false
    }
    
    /**
     * Sleep with readable name
     */
    fun delay(ms: Long) {
        Thread.sleep(ms)
    }
}
