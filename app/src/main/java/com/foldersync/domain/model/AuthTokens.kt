package com.foldersync.domain.model

data class AuthTokens(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAtEpochMillis: Long? = null
) {
    fun isExpired(now: Long = System.currentTimeMillis(), skewMillis: Long = 60_000): Boolean {
        val expires = expiresAtEpochMillis ?: return true
        return now + skewMillis >= expires
    }
}
