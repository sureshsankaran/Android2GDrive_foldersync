package com.foldersync.e2e

/**
 * Configuration for end-to-end tests
 * These tests require a physical device with:
 * - Google account signed in
 * - Internet connectivity
 * - User interaction for OAuth consent and folder selection
 */
object E2ETestConfig {
    
    /**
     * Timeout for waiting for UI elements (milliseconds)
     */
    const val UI_TIMEOUT_MS = 30_000L
    
    /**
     * Timeout for waiting for OAuth flow completion (milliseconds)
     * This is longer because user needs to interact with Google sign-in
     */
    const val OAUTH_TIMEOUT_MS = 120_000L
    
    /**
     * Timeout for sync operation completion (milliseconds)
     */
    const val SYNC_TIMEOUT_MS = 300_000L
    
    /**
     * Polling interval for checking conditions (milliseconds)
     */
    const val POLL_INTERVAL_MS = 1_000L
    
    /**
     * Short delay for UI animations (milliseconds)
     */
    const val UI_ANIMATION_DELAY_MS = 500L
    
    /**
     * Test folder name to create on device for sync testing
     */
    const val TEST_FOLDER_NAME = "FolderSync_E2E_Test"
    
    /**
     * Test file names for verification
     */
    val TEST_FILES = listOf(
        "test_file_1.txt",
        "test_file_2.txt",
        "test_image.png"
    )
}
