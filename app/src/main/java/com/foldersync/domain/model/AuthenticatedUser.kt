package com.foldersync.domain.model

data class AuthenticatedUser(
    val email: String,
    val displayName: String?,
    val avatarUrl: String?
)
