package com.foldersync.data.remote.drive

import android.util.Log
import com.foldersync.data.remote.drive.error.RateLimitException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.delay

@Singleton
class RateLimiter @Inject constructor() {

    private val recentQuotaHits = AtomicInteger(0)
    private val baseDelayMs = 1_000L
    private val maxDelayMs = 32_000L
    private val maxRetries = 5

    suspend fun <T> runWithBackoff(
        operationName: String,
        block: suspend (attempt: Int) -> T
    ): T {
        var attempt = 1
        var lastError: Throwable? = null
        while (attempt <= maxRetries) {
            try {
                return block(attempt)
            } catch (ex: RateLimitException) {
                lastError = ex
                logRateLimit(operationName, attempt, ex)
            }
            val delayMs = calculateDelay(attempt)
            delay(delayMs)
            attempt++
        }
        throw lastError ?: RateLimitException("Rate limit exceeded for $operationName")
    }

    private fun calculateDelay(attempt: Int): Long {
        val jitter = Random.nextLong(0, 500)
        val exponential = baseDelayMs shl (attempt - 1)
        return min(maxDelayMs, exponential) + jitter
    }

    private fun logRateLimit(operation: String, attempt: Int, ex: Exception) {
        recentQuotaHits.incrementAndGet()
        Log.w("RateLimiter", "Rate limited on $operation (attempt $attempt): ${ex.message}")
    }

    fun resetQuotaHitCount() {
        recentQuotaHits.set(0)
    }

    fun quotaHitCount(): Int = recentQuotaHits.get()
}
