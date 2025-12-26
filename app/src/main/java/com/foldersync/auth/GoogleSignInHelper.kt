package com.foldersync.auth

import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleSignInHelper @Inject constructor(
    private val signInClient: GoogleSignInClient
) {
    fun getSignInIntent(): Intent = signInClient.signInIntent

    suspend fun getAccountFromIntent(data: Intent?): GoogleSignInAccount {
        val intent = requireNotNull(data) { "Sign-in intent is null" }
        return GoogleSignIn.getSignedInAccountFromIntent(intent).await()
    }

    suspend fun silentSignIn(): GoogleSignInAccount? =
        try {
            signInClient.silentSignIn().await()
        } catch (_: Exception) {
            null
        }

    suspend fun signOut() {
        signInClient.signOut().await()
    }

    suspend fun revokeAccess() {
        signInClient.revokeAccess().await()
    }
}
