package com.foldersync.data.remote.drive

import com.foldersync.data.remote.drive.error.AuthenticationException
import com.foldersync.data.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.Response

@Singleton
class AuthInterceptor @Inject constructor(
    private val authRepository: AuthRepository
) : Interceptor {

    private val refreshMutex = Mutex()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val accessToken = runBlocking { authRepository.getAccessToken() }
        android.util.Log.d("AuthInterceptor", "Access token present: ${accessToken != null}, length: ${accessToken?.length ?: 0}")
        val authedRequest = accessToken?.let {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $it")
                .build()
        } ?: originalRequest

        val response = chain.proceed(authedRequest)
        
        // Handle 403 with "unregistered callers" - needs re-consent
        // Don't throw exception - just signal auth failure and return the response
        // The UI layer will handle showing re-login prompt
        if (response.code == 403) {
            val errorBody = response.peekBody(1024).string()
            if (errorBody.contains("unregistered callers") || errorBody.contains("PERMISSION_DENIED")) {
                android.util.Log.e("AuthInterceptor", "403 Permission denied - needs re-consent: $errorBody")
                runBlocking { authRepository.onAuthFailure() }
                // Return the response instead of throwing - let calling code handle gracefully
                return response
            }
        }
        
        if (response.code != 401) {
            return response
        }

        response.close()
        val refreshedToken = runBlocking {
            refreshMutex.withLock {
                authRepository.refreshToken()
            }
        }

        if (refreshedToken.isNullOrBlank()) {
            runBlocking { authRepository.onAuthFailure() }
            throw AuthenticationException("Failed to refresh access token")
        }

        val retried = authedRequest.newBuilder()
            .header("Authorization", "Bearer $refreshedToken")
            .build()

        val retryResponse = chain.proceed(retried)
        if (retryResponse.code == 401) {
            retryResponse.close()
            runBlocking { authRepository.onAuthFailure() }
            throw AuthenticationException("Authentication failed after token refresh")
        }
        return retryResponse
    }
}
