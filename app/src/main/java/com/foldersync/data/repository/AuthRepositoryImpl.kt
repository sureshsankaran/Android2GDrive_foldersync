package com.foldersync.data.repository

import android.content.Context
import android.content.Intent
import com.foldersync.auth.GoogleSignInHelper
import com.foldersync.auth.TokenRefreshManager
import com.foldersync.data.auth.SecureTokenManager
import com.foldersync.domain.model.AuthState
import com.foldersync.domain.model.AuthenticatedUser
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val signInHelper: GoogleSignInHelper,
    private val tokenRefreshManager: TokenRefreshManager,
    private val secureTokenManager: SecureTokenManager
) : AuthRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow<AuthState>(AuthState.SignedOut)

    override val authState: StateFlow<AuthState> = state.asStateFlow()

    init {
        scope.launch { bootstrapAuthState() }
    }

    override fun getSignInIntent(): Intent = signInHelper.getSignInIntent()

    override suspend fun handleSignInResult(data: Intent?): AuthState {
        state.value = AuthState.SigningIn
        return runCatching {
            val account = signInHelper.getAccountFromIntent(data)
            val tokens = tokenRefreshManager.persistTokensFromAccount(account)
                ?: error("Access token missing from sign-in result")

            if (tokens.accessToken.isNullOrBlank()) {
                error("Access token missing from sign-in result")
            }

            val user = account.toDomainUser()
            state.value = AuthState.Authenticated(user)
            state.value
        }.getOrElse { throwable ->
            val message = when (throwable) {
                is ApiException -> when (throwable.statusCode) {
                    GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> {
                        state.value = AuthState.SignedOut
                        return@getOrElse state.value
                    }

                    GoogleSignInStatusCodes.SIGN_IN_REQUIRED -> "Sign-in required"
                    else -> throwable.message ?: "Authentication failed"
                }
                else -> throwable.message ?: "Authentication failed"
            }
            state.value = AuthState.Error(message = message, recoverable = true)
            state.value
        }
    }

    override suspend fun silentSignIn(): AuthState {
        state.value = AuthState.Refreshing
        val account = signInHelper.silentSignIn()
        if (account == null) {
            state.value = AuthState.SignedOut
            return state.value
        }

        val token = tokenRefreshManager.getValidAccessToken(forceRefresh = true)
        if (token.isNullOrBlank()) {
            state.value = AuthState.Error(
                message = "Silent sign-in failed",
                recoverable = true
            )
            return state.value
        }

        state.value = AuthState.Authenticated(account.toDomainUser())
        return state.value
    }

    override suspend fun signOut() {
        tokenRefreshManager.clearTokens()
        signInHelper.signOut()
        signInHelper.revokeAccess()
        state.value = AuthState.SignedOut
    }

    override suspend fun isAuthenticated(): Boolean {
        val current = state.value
        if (current is AuthState.Authenticated) return true
        return !secureTokenManager.getTokens().accessToken.isNullOrBlank()
    }

    override fun getCurrentUser(): AuthenticatedUser? =
        when (val current = state.value) {
            is AuthState.Authenticated -> current.user
            else -> GoogleSignIn.getLastSignedInAccount(context)?.toDomainUser()
        }
    
    override suspend fun getAccessToken(): String? {
        return tokenRefreshManager.getValidAccessToken()
    }
    
    override suspend fun refreshToken(): String? {
        return tokenRefreshManager.getValidAccessToken(forceRefresh = true)
    }
    
    override suspend fun onAuthFailure() {
        tokenRefreshManager.clearTokens()
        state.value = AuthState.Error(
            message = "Authentication expired",
            recoverable = true
        )
    }

    private suspend fun bootstrapAuthState() {
        val account = signInHelper.silentSignIn()
        if (account != null) {
            val token = tokenRefreshManager.getValidAccessToken()
            if (!token.isNullOrBlank()) {
                state.value = AuthState.Authenticated(account.toDomainUser())
                return
            }
        }
        state.value = AuthState.SignedOut
    }

    private fun GoogleSignInAccount.toDomainUser() = AuthenticatedUser(
        email = email.orEmpty(),
        displayName = displayName,
        avatarUrl = photoUrl?.toString()
    )
}
