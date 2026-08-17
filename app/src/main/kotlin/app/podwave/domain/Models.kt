package app.podwave.domain

import app.podwave.rss.EpisodeType
import java.time.Instant

data class Podcast(
    val id: Long,
    val feedUrl: String,
    val title: String,
    val author: String?,
    val description: String?,
    val artworkUrl: String?,
    val link: String?,
    val categories: List<String>,
    val subscribedAt: Instant,
    val lastRefreshedAt: Instant?,
    val lastRefreshError: String?,
)

enum class DownloadState { None, Queued, Downloading, Downloaded, Failed }

data class Episode(
    val id: Long,
    val podcastId: Long,
    val podcastTitle: String,
    val title: String,
    val description: String?,
    val audioUrl: String,
    val durationSeconds: Long?,
    val publishedAt: Instant?,
    /** Episode artwork, falling back to the podcast's. */
    val artworkUrl: String?,
    val season: Int?,
    val episodeNumber: Int?,
    val type: EpisodeType,
    val playbackPositionMs: Long,
    val completed: Boolean,
    val downloadState: DownloadState,
    val downloadProgress: Int,
    val localFilePath: String?,
) {
    val durationMs: Long? get() = durationSeconds?.times(1000)

    /** 0..1 listening progress, or null if unknown or unplayed. */
    val progress: Float?
        get() {
            val total = durationMs ?: return null
            if (total <= 0 || playbackPositionMs <= 0) return null
            return (playbackPositionMs.toFloat() / total).coerceIn(0f, 1f)
        }

    val isInProgress: Boolean get() = !completed && playbackPositionMs > 0

    /** Prefer the downloaded file, fall back to streaming. */
    val playbackUri: String get() = localFilePath?.let { "file://$it" } ?: audioUrl
}

/** A podcast found through the directory search, not yet subscribed. */
data class DirectoryPodcast(
    val title: String,
    val author: String?,
    val feedUrl: String,
    val artworkUrl: String?,
    val genre: String?,
    val episodeCount: Int?,
)
