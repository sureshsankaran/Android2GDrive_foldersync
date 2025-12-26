package com.foldersync.data.repository

import android.content.Intent
import com.foldersync.domain.model.AuthState
import com.foldersync.domain.model.AuthenticatedUser
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authState: StateFlow<AuthState>

    fun getSignInIntent(): Intent

    suspend fun handleSignInResult(data: Intent?): AuthState

    suspend fun silentSignIn(): AuthState

    suspend fun signOut()

    suspend fun isAuthenticated(): Boolean

    fun getCurrentUser(): AuthenticatedUser?
    
    /**
     * Get the current access token
     */
    suspend fun getAccessToken(): String?
    
    /**
     * Refresh the access token
     * @return the new access token or null if refresh failed
     */
    suspend fun refreshToken(): String?
    
    /**
     * Handle authentication failure - clear tokens and update state
     */
    suspend fun onAuthFailure()
}
