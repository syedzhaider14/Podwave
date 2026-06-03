package dev.podwave.model

import java.time.Instant

/** A podcast, identified by its RSS feed URL. */
data class Podcast(
    val feedUrl: String,
    val title: String,
    val author: String,
    val description: String,
    val artworkUrl: String?,
    val websiteUrl: String?,
    val categories: List<String>,
    val subscribed: Boolean,
    val lastRefreshedAt: Instant?,
)

enum class DownloadStatus { None, Queued, Downloading, Downloaded, Failed }

data class Episode(
    /** Stable id derived from the feed URL and the item's guid; see [episodeId]. */
    val id: String,
    val podcastFeedUrl: String,
    val podcastTitle: String,
    val title: String,
    /** Show notes, usually HTML. */
    val description: String,
    val audioUrl: String,
    val audioMimeType: String?,
    val audioSizeBytes: Long?,
    val durationMs: Long?,
    val publishedAt: Instant?,
    val artworkUrl: String?,
    val season: Int?,
    val episodeNumber: Int?,
    val explicit: Boolean,
    val positionMs: Long,
    val playedAt: Instant?,
    val downloadStatus: DownloadStatus,
    val downloadProgress: Int,
    val localFilePath: String?,
) {
    val isPlayed: Boolean get() = playedAt != null

    val isInProgress: Boolean get() = !isPlayed && positionMs > 0

    /** Plays the local copy when it exists, otherwise streams. */
    val playbackUri: String get() = localFilePath?.let { "file://$it" } ?: audioUrl

    val remainingMs: Long? get() = durationMs?.let { (it - positionMs).coerceAtLeast(0) }
}

/** A podcast as returned by the iTunes directory, before the user has opened its feed. */
data class DirectoryPodcast(
    val itunesId: Long,
    val title: String,
    val author: String,
    val artworkUrl: String?,
    val feedUrl: String?,
    val genre: String?,
)

enum class ThemeMode { System, Light, Dark }

data class AppSettings(
    val playbackSpeed: Float = 1f,
    val skipBackSeconds: Int = 10,
    val skipForwardSeconds: Int = 30,
    val autoDownloadNewEpisodes: Boolean = false,
    val downloadOnWifiOnly: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.System,
) {
    companion object {
        val PlaybackSpeeds = listOf(0.8f, 1f, 1.2f, 1.5f, 1.75f, 2f)
        val SkipBackOptions = listOf(5, 10, 15, 30)
        val SkipForwardOptions = listOf(15, 30, 45, 60)
    }
}
