package dev.podwave.rss

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Parses `<itunes:duration>`, which appears in the wild as `HH:MM:SS`, `MM:SS`, plain seconds,
 * or with fractional seconds. Returns milliseconds, or null when unreadable or zero.
 */
fun parseDurationMs(raw: String?): Long? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val parts = value.split(':')
    if (parts.size > 3) return null

    var seconds = 0.0
    for (part in parts) {
        val number = part.trim().toDoubleOrNull() ?: return null
        if (number < 0) return null
        seconds = seconds * 60 + number
    }
    val millis = (seconds * 1000).toLong()
    return millis.takeIf { it > 0 }
}

private val zoneAbbreviations = mapOf(
    "UT" to "+0000",
    "Z" to "+0000",
    "EST" to "-0500",
    "EDT" to "-0400",
    "CST" to "-0600",
    "CDT" to "-0500",
    "MST" to "-0700",
    "MDT" to "-0600",
    "PST" to "-0800",
    "PDT" to "-0700",
)

private val rfc822Variants = listOf(
