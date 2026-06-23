package dev.podwave.ui.common

/**
 * Show notes and podcast descriptions arrive as anything from plain text to full HTML documents.
 * This turns them into readable plain text for previews; the episode screen renders real HTML.
 */
object Html {
    private val paragraphEnd = Regex("""(?i)</\s*(p|div|h[1-6]|ul|ol|blockquote)\s*>""")
    private val lineBreak = Regex("""(?i)<\s*br\s*/?\s*>|</\s*li\s*>""")
    private val listItem = Regex("""(?i)<\s*li(\s[^>]*)?>""")
    private val anyTag = Regex("""<[^>]*>""")
    private val numericEntity = Regex("""&#([xX]?)([0-9a-fA-F]+);""")
    private val namedEntities = listOf(
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&apos;" to "'",
        "&nbsp;" to " ",
        "&hellip;" to "…",
        "&mdash;" to "—",
        "&ndash;" to "–",
        "&rsquo;" to "’",
        "&lsquo;" to "‘",
        "&rdquo;" to "”",
        "&ldquo;" to "“",
        // Last, so "&amp;lt;" becomes "&lt;" and not "<".
        "&amp;" to "&",
    )
    private val spaces = Regex("""[ \t\u000B\f\r ]+""")
    private val extraBlankLines = Regex("""\n{3,}""")
    private val tagLike = Regex("""<\s*/?\s*[a-zA-Z][^>]*>""")

    fun looksLikeHtml(text: String): Boolean = tagLike.containsMatchIn(text)

    fun toPlainText(html: String): String {
        val withoutTags = html
            .replace(paragraphEnd, "\n\n")
            .replace(lineBreak, "\n")
            .replace(listItem, "• ")
            .replace(anyTag, "")
        return decodeEntities(withoutTags)
            .lines()
            .joinToString("\n") { it.replace(spaces, " ").trim() }
            .replace(extraBlankLines, "\n\n")
            .trim()
    }

    fun decodeEntities(text: String): String {
        val numeric = numericEntity.replace(text) { match ->
            val radix = if (match.groupValues[1].isEmpty()) 10 else 16
            val codePoint = match.groupValues[2].toIntOrNull(radix)
            if (codePoint != null && Character.isValidCodePoint(codePoint)) {
                String(Character.toChars(codePoint))
            } else {
                match.value
            }
        }
        return namedEntities.fold(numeric) { acc, (entity, value) -> acc.replace(entity, value) }
    }
}
