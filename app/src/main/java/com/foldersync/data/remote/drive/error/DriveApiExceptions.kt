package com.foldersync.data.remote.drive.error

class AuthenticationException(message: String) : Exception(message)

class DriveApiException(
    message: String,
    val statusCode: Int? = null,
    val errorBody: String? = null
) : Exception(message)

class RateLimitException(message: String) : Exception(message)
