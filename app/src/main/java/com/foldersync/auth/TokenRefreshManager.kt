package com.foldersync.auth

import android.accounts.Account
import android.content.Context
import com.foldersync.data.auth.SecureTokenManager
import com.foldersync.domain.model.AuthTokens
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class TokenRefreshManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val signInHelper: GoogleSignInHelper,
    private val secureTokenManager: SecureTokenManager
) {

    private val mutex = Mutex()
    
    // Cache the last known good account to avoid repeated silentSignIn calls
    private var cachedAccount: GoogleSignInAccount? = null

    suspend fun getValidAccessToken(forceRefresh: Boolean = false): String? = mutex.withLock {
        val currentTokens = secureTokenManager.getTokens()
        
        // If token is still valid and no force refresh, return it
        if (!forceRefresh && !currentTokens.isExpired() && !currentTokens.accessToken.isNullOrBlank()) {
            android.util.Log.d("TokenRefreshManager", "Token still valid, using cached")
            return currentTokens.accessToken
        }

        android.util.Log.d("TokenRefreshManager", "Token expired or force refresh, refreshing...")
        val refreshed = refreshTokenInternal()
        return refreshed?.accessToken
    }

    suspend fun persistTokens(tokens: AuthTokens) = mutex.withLock {
        secureTokenManager.saveTokens(tokens)
    }

    suspend fun clearTokens() = mutex.withLock {
        cachedAccount = null
        secureTokenManager.clear()
    }

    suspend fun persistTokensFromAccount(account: GoogleSignInAccount): AuthTokens? = mutex.withLock {
        // Cache the account for future token refreshes
        cachedAccount = account
        
        val accessToken = fetchAccessToken(account.account) ?: return@withLock null
        if (accessToken.isBlank()) return@withLock null
        val tokens = AuthTokens(
            accessToken = accessToken,
            refreshToken = account.serverAuthCode,
            expiresAtEpochMillis = System.currentTimeMillis() +
                TimeUnit.MINUTES.toMillis(AuthConfig.TOKEN_FALLBACK_LIFETIME_MINUTES)
        )
        secureTokenManager.saveTokens(tokens)
        tokens
    }

    private suspend fun refreshTokenInternal(): AuthTokens? {
        // Try cached account first, then silentSignIn, then check last signed in account
        var account = cachedAccount
        
        if (account == null) {
            android.util.Log.d("TokenRefreshManager", "No cached account, trying silentSignIn")
            account = signInHelper.silentSignIn()
        }
        
        if (account == null) {
            android.util.Log.d("TokenRefreshManager", "silentSignIn failed, checking last signed in account")
            account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
        }
        
        if (account == null) {
            android.util.Log.w("TokenRefreshManager", "No account available for token refresh")
            return null
        }
        
        // Cache for future use
        cachedAccount = account
        
        val accessToken = fetchAccessToken(account.account)
        if (accessToken.isNullOrBlank()) {
            android.util.Log.w("TokenRefreshManager", "Failed to fetch access token")
            return null
        }
        
        val expiresAt = System.currentTimeMillis() +
            TimeUnit.MINUTES.toMillis(AuthConfig.TOKEN_FALLBACK_LIFETIME_MINUTES)

        val tokens = AuthTokens(
            accessToken = accessToken,
            refreshToken = account.serverAuthCode,
            expiresAtEpochMillis = expiresAt
        )
        secureTokenManager.saveTokens(tokens)
        android.util.Log.d("TokenRefreshManager", "Token refreshed successfully, expires in ${AuthConfig.TOKEN_FALLBACK_LIFETIME_MINUTES} min")
        return tokens
    }

    private suspend fun fetchAccessToken(account: Account?): String? = withContext(Dispatchers.IO) {
        if (account == null) return@withContext null
        try {
            // Clear any cached token to force a fresh one from Google
            val currentToken = secureTokenManager.getAccessToken()
            if (currentToken != null) {
                GoogleAuthUtil.clearToken(context, currentToken)
            }
            GoogleAuthUtil.getToken(context, account, AuthConfig.scopeString)
        } catch (recoverable: UserRecoverableAuthException) {
            // User needs to re-consent - clear cached tokens so they can sign in again
            android.util.Log.w("TokenRefreshManager", "UserRecoverableAuthException - need consent", recoverable)
            cachedAccount = null
            secureTokenManager.clear()
            null
        } catch (e: GoogleAuthException) {
            android.util.Log.e("TokenRefreshManager", "GoogleAuthException", e)
            null
        } catch (e: IOException) {
            android.util.Log.e("TokenRefreshManager", "IOException getting token", e)
            null
        }
    }
}
