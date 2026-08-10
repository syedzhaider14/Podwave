package app.podwave.rss

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Parses RSS `pubDate` values. The spec says RFC 822, but real feeds also
 * use named US time zones, two-digit years, missing weekdays, wrong weekdays,
 * or ISO 8601. This tries the strict format first and then progressively
 * more forgiving ones.
 */
object Rfc822Dates {
    private val ZONE_ALIASES = mapOf(
        "UT" to "+0000", "UTC" to "+0000", "Z" to "+0000",
        "EST" to "-0500", "EDT" to "-0400",
        "CST" to "-0600", "CDT" to "-0500",
        "MST" to "-0700", "MDT" to "-0600",
        "PST" to "-0800", "PDT" to "-0700",
        "BST" to "+0100", "CET" to "+0100", "CEST" to "+0200",
    )

    private val fallbackFormats = listOf(
        "d MMM yyyy HH:mm:ss Z",
        "d MMM yyyy HH:mm Z",
        "d MMM yy HH:mm:ss Z",
    ).map { DateTimeFormatter.ofPattern(it, Locale.US) }

    fun parse(raw: String?): Instant? {
        val value = raw?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
        if (value.isEmpty()) return null

        tryParse { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }?.let { return it }
        tryParse { Instant.parse(value) }?.let { return it }
        tryParse { ZonedDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() }?.let { return it }

        // Drop the (possibly wrong) weekday and normalise named zones.
        val withoutWeekday = value.substringAfter(", ", value)
        val normalized = ZONE_ALIASES.entries.fold(withoutWeekday) { acc, (name, offset) ->
            acc.replace(Regex("\\b$name$"), offset)
        }
        for (format in fallbackFormats) {
            tryParse { ZonedDateTime.parse(normalized, format).toInstant() }?.let { return it }
        }
        return null
    }

    private inline fun tryParse(block: () -> Instant): Instant? =
        try {
            block()
        } catch (_: DateTimeParseException) {
            null
        }
}
