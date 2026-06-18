package dev.podwave.download

/** Pure helpers for naming downloaded files and reporting progress. */
object DownloadFiles {
    private const val DEFAULT_EXTENSION = "mp3"

    /** `<episode id>.<ext>`; the id is a hex digest, so the name is always filesystem-safe. */
    fun fileName(episodeId: String, url: String): String = "$episodeId.${extensionFor(url)}"

    /** 0–100, or 0 while the size is unknown (the notification shows an indeterminate bar). */
    fun progressPercent(copied: Long, total: Long?): Int =
        if (total == null || total <= 0) 0 else ((copied * 100) / total).toInt().coerceIn(0, 100)

    /**
     * The extension from the URL path, ignoring query strings that ad-insertion and tracking
     * services append. Falls back to mp3, which is what almost every podcast uses anyway.
     */
    fun extensionFor(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('/', "").substringAfterLast('.', "").lowercase()
        return ext.takeIf { it.length in 2..4 && it.all(Char::isLetterOrDigit) } ?: DEFAULT_EXTENSION
    }
}
