package com.foldersync.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object DateTimeUtils {

    private val displayFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
    private val shortFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")
    private val timeOnlyFormatter = DateTimeFormatter.ofPattern("h:mm a")

    /**
     * Format timestamp for display
     */
    fun formatTimestamp(timestamp: Long): String {
        val dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp),
            ZoneId.systemDefault()
        )
        return dateTime.format(displayFormatter)
    }

    /**
     * Format timestamp as relative time (e.g., "5 minutes ago")
     */
    fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> {
                val minutes = diff / 60_000
                "$minutes minute${if (minutes == 1L) "" else "s"} ago"
            }
            diff < 86400_000 -> {
                val hours = diff / 3600_000
                "$hours hour${if (hours == 1L) "" else "s"} ago"
            }
            diff < 604800_000 -> {
                val days = diff / 86400_000
                "$days day${if (days == 1L) "" else "s"} ago"
            }
            else -> formatTimestamp(timestamp)
        }
    }

    /**
     * Format for short display (today shows time only)
     */
    fun formatShort(timestamp: Long): String {
        val dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp),
            ZoneId.systemDefault()
        )
        val now = LocalDateTime.now()

        return when {
            dateTime.toLocalDate() == now.toLocalDate() -> dateTime.format(timeOnlyFormatter)
            dateTime.year == now.year -> dateTime.format(shortFormatter)
            else -> dateTime.format(displayFormatter)
        }
    }

    /**
     * Parse ISO 8601 date string to epoch millis
     */
    fun parseIso8601(dateString: String): Long? {
        return try {
            Instant.parse(dateString).toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get epoch millis from Instant
     */
    fun toEpochMillis(instant: Instant?): Long? = instant?.toEpochMilli()

    /**
     * Calculate if timestamp is older than given duration
     */
    fun isOlderThan(timestamp: Long, amount: Long, unit: ChronoUnit): Boolean {
        val then = Instant.ofEpochMilli(timestamp)
        val cutoff = Instant.now().minus(amount, unit)
        return then.isBefore(cutoff)
    }
}
