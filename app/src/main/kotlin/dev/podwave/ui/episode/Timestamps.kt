package dev.podwave.ui.episode

/**
 * Finds chapter-style timestamps ("12:34", "1:02:03") in show notes so they can be tapped to
 * jump to that point in the episode.
 */
object Timestamps {
    data class Match(val range: IntRange, val positionMs: Long)

    // Not preceded or followed by another digit or colon, so pieces of longer runs like "2024:10:02" don't match.
    private val pattern = Regex("""(?<![\d:])(?:(\d{1,2}):)?([0-5]?\d):([0-5]\d)(?![\d:])""")

    /** Matches beyond [maxMs] (the episode's length, if known) are dropped as false positives. */
    fun find(text: String, maxMs: Long? = null): List<Match> =
        pattern.findAll(text).mapNotNull { match ->
            val hours = match.groupValues[1].toLongOrNull() ?: 0
            val minutes = match.groupValues[2].toLong()
            val seconds = match.groupValues[3].toLong()
            val positionMs = ((hours * 60 + minutes) * 60 + seconds) * 1_000
            if (maxMs != null && positionMs > maxMs) null else Match(match.range, positionMs)
        }.toList()
}
