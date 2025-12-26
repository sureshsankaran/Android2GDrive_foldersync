package com.foldersync.domain.model

sealed class AuthState {
    object SignedOut : AuthState()
    object SigningIn : AuthState()
    object Refreshing : AuthState()
    data class Authenticated(val user: AuthenticatedUser) : AuthState()
    data class Error(val message: String, val recoverable: Boolean = false) : AuthState()
}
