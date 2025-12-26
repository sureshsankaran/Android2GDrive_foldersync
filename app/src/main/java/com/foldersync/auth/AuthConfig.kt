package com.foldersync.auth

import com.foldersync.BuildConfig
import com.google.api.services.drive.DriveScopes

object AuthConfig {
    // OAuth Web Client ID - set via local.properties or environment variable
    // DO NOT commit actual credentials to version control
    val WEB_CLIENT_ID: String = BuildConfig.WEB_CLIENT_ID

    val DRIVE_SCOPES = listOf(
        DriveScopes.DRIVE_FILE,
        DriveScopes.DRIVE_READONLY
    )

    const val TOKEN_FALLBACK_LIFETIME_MINUTES = 55L
    private const val TOKEN_PREFIX = "oauth2:"

    val scopeString: String
        get() = TOKEN_PREFIX + DRIVE_SCOPES.joinToString(" ") { it }
}
