package dev.podwave.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.podwave.model.DownloadStatus
import dev.podwave.model.Episode
import dev.podwave.model.Podcast
import java.time.Instant

@Entity(tableName = "podcasts")
data class PodcastEntity(
    @PrimaryKey @ColumnInfo(name = "feed_url") val feedUrl: String,
    val title: String,
    val author: String,
    val description: String,
    @ColumnInfo(name = "artwork_url") val artworkUrl: String?,
    @ColumnInfo(name = "website_url") val websiteUrl: String?,
    val categories: List<String>,
    val subscribed: Boolean,
    @ColumnInfo(name = "subscribed_at") val subscribedAt: Instant?,
    @ColumnInfo(name = "last_refreshed_at") val lastRefreshedAt: Instant?,
    /** HTTP ETag from the last fetch, for conditional refreshes. */
    val etag: String?,
)

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = PodcastEntity::class,
            parentColumns = ["feed_url"],
            childColumns = ["podcast_feed_url"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("podcast_feed_url"), Index("published_at"), Index("last_played_at")],
)
data class EpisodeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "podcast_feed_url") val podcastFeedUrl: String,
    val guid: String,
    val title: String,
    val description: String,
    @ColumnInfo(name = "audio_url") val audioUrl: String,
    @ColumnInfo(name = "audio_mime_type") val audioMimeType: String?,
    @ColumnInfo(name = "audio_size_bytes") val audioSizeBytes: Long?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    @ColumnInfo(name = "published_at") val publishedAt: Instant?,
    @ColumnInfo(name = "artwork_url") val artworkUrl: String?,
    val season: Int?,
    @ColumnInfo(name = "episode_number") val episodeNumber: Int?,
    val explicit: Boolean,
    // Listener state; preserved when the feed is refreshed.
    @ColumnInfo(name = "position_ms") val positionMs: Long = 0,
    @ColumnInfo(name = "played_at") val playedAt: Instant? = null,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Instant? = null,
    @ColumnInfo(name = "download_status") val downloadStatus: DownloadStatus = DownloadStatus.None,
    @ColumnInfo(name = "download_progress") val downloadProgress: Int = 0,
    @ColumnInfo(name = "local_file_path") val localFilePath: String? = null,
    @ColumnInfo(name = "added_at") val addedAt: Instant,
)

/** Episodes are almost always shown with their show's name, so queries join it in. */
data class EpisodeWithPodcast(
    @Embedded val episode: EpisodeEntity,
    @ColumnInfo(name = "podcast_title") val podcastTitle: String,
    @ColumnInfo(name = "podcast_artwork_url") val podcastArtworkUrl: String?,
)

@Entity(
    tableName = "queue",
    foreignKeys = [
        ForeignKey(
            entity = EpisodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["episode_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class QueueEntity(
    @PrimaryKey @ColumnInfo(name = "episode_id") val episodeId: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Long,
)

fun PodcastEntity.asModel() = Podcast(
    feedUrl = feedUrl,
    title = title,
    author = author,
    description = description,
    artworkUrl = artworkUrl,
    websiteUrl = websiteUrl,
    categories = categories,
    subscribed = subscribed,
    lastRefreshedAt = lastRefreshedAt,
)

fun EpisodeWithPodcast.asModel() = Episode(
    id = episode.id,
    podcastFeedUrl = episode.podcastFeedUrl,
    podcastTitle = podcastTitle,
    title = episode.title,
    description = episode.description,
    audioUrl = episode.audioUrl,
    audioMimeType = episode.audioMimeType,
    audioSizeBytes = episode.audioSizeBytes,
    durationMs = episode.durationMs,
    publishedAt = episode.publishedAt,
    artworkUrl = episode.artworkUrl ?: podcastArtworkUrl,
    season = episode.season,
    episodeNumber = episode.episodeNumber,
    explicit = episode.explicit,
    positionMs = episode.positionMs,
    playedAt = episode.playedAt,
    downloadStatus = episode.downloadStatus,
    downloadProgress = episode.downloadProgress,
    localFilePath = episode.localFilePath,
)
