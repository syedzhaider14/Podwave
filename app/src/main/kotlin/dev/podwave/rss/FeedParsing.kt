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
    DateTimeFormatter.RFC_1123_DATE_TIME,
    DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm Z", Locale.ENGLISH),
    DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
    DateTimeFormatter.ofPattern("EEE, d MMMM yyyy HH:mm:ss Z", Locale.ENGLISH),
)

/**
 * Parses RSS `pubDate` values. The spec says RFC 822, but feeds also use named US time zones,
 * two-digit days without the weekday, full month names and ISO-8601. Returns null rather than
 * guessing when nothing matches.
 */
fun parsePubDate(raw: String?): Instant? {
    var value = raw?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() } ?: return null

    val zone = value.substringAfterLast(' ')
    zoneAbbreviations[zone.uppercase(Locale.ROOT)]?.let { offset ->
        value = value.dropLast(zone.length) + offset
    }

    // Some feeds get the weekday wrong ("Mon, 2 Oct 2002" was a Wednesday), which strict
    // RFC 1123 parsing rejects; retrying without the weekday recovers the date.
    val candidates = listOf(value, value.substringAfter(", ", value))
    for (candidate in candidates) {
        for (formatter in rfc822Variants) {
            try {
                return ZonedDateTime.parse(candidate, formatter).toInstant()
            } catch (_: DateTimeParseException) {
                // Try the next variant.
            }
        }
    }
    return try {
        ZonedDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }
}

/** `yes`, `true` and `explicit` all mean explicit; anything else (including `clean`) does not. */
fun parseExplicit(raw: String?): Boolean =
    raw?.trim()?.lowercase(Locale.ROOT) in setOf("yes", "true", "explicit")
