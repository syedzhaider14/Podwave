package dev.podwave.testing

import dev.podwave.data.db.EpisodeEntity
import dev.podwave.model.DownloadStatus
import dev.podwave.model.Episode
import dev.podwave.model.Podcast
import dev.podwave.rss.ParsedEpisode
import java.time.Instant

const val FEED_URL = "https://feeds.example.com/signal"

fun testEpisode(
    id: String = "ep-1",
    title: String = "Episode",
    durationMs: Long? = 3_600_000,
    positionMs: Long = 0,
    playedAt: Instant? = null,
    downloadStatus: DownloadStatus = DownloadStatus.None,
    localFilePath: String? = null,
    audioSizeBytes: Long? = null,
) = Episode(
    id = id,
    podcastFeedUrl = FEED_URL,
    podcastTitle = "Signal & Noise",
    title = title,
    description = "",
    audioUrl = "https://cdn.example.com/$id.mp3",
    audioMimeType = "audio/mpeg",
    audioSizeBytes = audioSizeBytes,
    durationMs = durationMs,
    publishedAt = Instant.parse("2024-10-02T13:00:00Z"),
    artworkUrl = null,
    season = null,
    episodeNumber = null,
    explicit = false,
    positionMs = positionMs,
    playedAt = playedAt,
    downloadStatus = downloadStatus,
    downloadProgress = 0,
    localFilePath = localFilePath,
)

fun parsedEpisode(
    guid: String,
    title: String = "Episode $guid",
    audioUrl: String = "https://cdn.example.com/$guid.mp3",
    durationMs: Long? = 1_800_000,
    publishedAt: Instant? = Instant.parse("2024-10-01T12:00:00Z"),
) = ParsedEpisode(
    guid = guid,
    title = title,
    description = "Notes for $guid",
    audioUrl = audioUrl,
    audioMimeType = "audio/mpeg",
    audioSizeBytes = null,
    durationMs = durationMs,
    publishedAt = publishedAt,
    artworkUrl = null,
    season = null,
    episodeNumber = null,
    explicit = false,
)

fun episodeEntity(
    id: String,
    guid: String,
    title: String = "Stored $guid",
    positionMs: Long = 0,
    playedAt: Instant? = null,
    downloadStatus: DownloadStatus = DownloadStatus.None,
    localFilePath: String? = null,
    durationMs: Long? = null,
    publishedAt: Instant? = null,
    addedAt: Instant = Instant.parse("2024-01-01T00:00:00Z"),
) = EpisodeEntity(
    id = id,
    podcastFeedUrl = FEED_URL,
    guid = guid,
    title = title,
    description = "",
    audioUrl = "https://old.example.com/$guid.mp3",
    audioMimeType = "audio/mpeg",
    audioSizeBytes = null,
    durationMs = durationMs,
    publishedAt = publishedAt,
    artworkUrl = null,
    season = null,
    episodeNumber = null,
    explicit = false,
    positionMs = positionMs,
    playedAt = playedAt,
    downloadStatus = downloadStatus,
    localFilePath = localFilePath,
    addedAt = addedAt,
)

fun testPodcast(feedUrl: String = FEED_URL, title: String = "Signal & Noise", subscribed: Boolean = true) = Podcast(
    feedUrl = feedUrl,
    title = title,
    author = "Example Media",
    description = "",
    artworkUrl = null,
    websiteUrl = null,
    categories = emptyList(),
    subscribed = subscribed,
    lastRefreshedAt = null,
)
