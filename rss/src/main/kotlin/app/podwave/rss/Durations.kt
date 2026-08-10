package app.podwave.rss

/**
 * Parses `itunes:duration`, which feeds publish in several formats:
 * "3723", "3723.5", "62:03", "1:02:03" (and occasionally with whitespace).
 *
 * @return the duration in whole seconds, or null when the value is missing or garbage.
 */
fun parseItunesDuration(raw: String?): Long? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null

    if (':' !in value) {
        return value.toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() }?.toLong()
    }

    val parts = value.split(':')
    if (parts.size > 3 || parts.any { it.isBlank() }) return null
    val numbers = parts.map { it.trim().toDoubleOrNull() ?: return null }
    if (numbers.any { it < 0 }) return null

    return numbers.fold(0.0) { total, part -> total * 60 + part }.toLong()
}

/** Formats seconds as "1:02:03" or "2:03". */
fun formatDuration(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
