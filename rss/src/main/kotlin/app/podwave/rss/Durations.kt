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

