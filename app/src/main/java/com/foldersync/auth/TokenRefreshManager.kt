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

    suspend fun getValidAccessToken(forceRefresh: Boolean = false): String? = mutex.withLock {
        val currentTokens = secureTokenManager.getTokens()
        val needsRefresh = forceRefresh || currentTokens.isExpired() || currentTokens.accessToken.isNullOrBlank()

        if (!needsRefresh) {
            return currentTokens.accessToken
        }

        val refreshed = refreshTokenInternal()
        return refreshed?.accessToken
    }

    suspend fun persistTokens(tokens: AuthTokens) = mutex.withLock {
        secureTokenManager.saveTokens(tokens)
    }

    suspend fun clearTokens() = mutex.withLock {
        secureTokenManager.clear()
    }

    suspend fun persistTokensFromAccount(account: GoogleSignInAccount): AuthTokens? = mutex.withLock {
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
        val account = signInHelper.silentSignIn() ?: return null
        val accessToken = fetchAccessToken(account.account)
        if (accessToken.isNullOrBlank()) {
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
        return tokens
    }

    private suspend fun fetchAccessToken(account: Account?): String? = withContext(Dispatchers.IO) {
        if (account == null) return@withContext null
        try {
            GoogleAuthUtil.getToken(context, account, AuthConfig.scopeString)
        } catch (recoverable: UserRecoverableAuthException) {
            null
        } catch (_: GoogleAuthException) {
            null
        } catch (_: IOException) {
            null
        }
    }
}
