package com.foldersync.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenRefreshInterceptor @Inject constructor(
    private val tokenRefreshManager: TokenRefreshManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        val accessToken = runBlocking { tokenRefreshManager.getValidAccessToken() }
        if (!accessToken.isNullOrBlank()) {
            request = request.newBuilder()
                .header(AUTH_HEADER, "Bearer $accessToken")
                .build()
        }

        val response = chain.proceed(request)
        if (response.code != 401) {
            return response
        }

        val refreshedToken = runBlocking { tokenRefreshManager.getValidAccessToken(forceRefresh = true) }
        if (refreshedToken.isNullOrBlank()) {
            return response
        }

        response.close()
        val retryRequest = request.newBuilder()
            .header(AUTH_HEADER, "Bearer $refreshedToken")
            .build()

        return chain.proceed(retryRequest)
    }

    private companion object {
        const val AUTH_HEADER = "Authorization"
    }
}
