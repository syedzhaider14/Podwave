package app.podwave.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.podwave.domain.DownloadState
import app.podwave.domain.Episode
import app.podwave.domain.Podcast
import app.podwave.rss.EpisodeType
import java.time.Instant

@Entity(tableName = "podcasts", indices = [Index(value = ["feed_url"], unique = true)])
data class PodcastEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "feed_url") val feedUrl: String,
    val title: String,
    val author: String?,
    val description: String?,
    @ColumnInfo(name = "artwork_url") val artworkUrl: String?,
    val link: String?,
    /** Newline-separated; categories never contain newlines. */
    val categories: String,
    @ColumnInfo(name = "subscribed_at") val subscribedAt: Long,
    @ColumnInfo(name = "last_refreshed_at") val lastRefreshedAt: Long?,
    @ColumnInfo(name = "last_refresh_error") val lastRefreshError: String?,
)

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = PodcastEntity::class,
            parentColumns = ["id"],
            childColumns = ["podcast_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["podcast_id", "guid"], unique = true),
        Index(value = ["published_at"]),
    ],
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "podcast_id") val podcastId: Long,
    val guid: String,
    val title: String,
    val description: String?,
    @ColumnInfo(name = "audio_url") val audioUrl: String,
    @ColumnInfo(name = "audio_type") val audioType: String?,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Long?,
    @ColumnInfo(name = "published_at") val publishedAt: Long?,
    @ColumnInfo(name = "artwork_url") val artworkUrl: String?,
    val season: Int?,
    @ColumnInfo(name = "episode_number") val episodeNumber: Int?,
    val type: EpisodeType,
    @ColumnInfo(name = "playback_position_ms") val playbackPositionMs: Long = 0,
    val completed: Boolean = false,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long? = null,
    @ColumnInfo(name = "download_state") val downloadState: DownloadState = DownloadState.None,
    @ColumnInfo(name = "download_progress") val downloadProgress: Int = 0,
    @ColumnInfo(name = "local_file_path") val localFilePath: String? = null,
    @ColumnInfo(name = "discovered_at") val discoveredAt: Long,
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
data class QueueEntryEntity(
    @PrimaryKey @ColumnInfo(name = "episode_id") val episodeId: Long,
    val position: Int,
)

/** An episode joined with the fields of its podcast that lists need. */
data class EpisodeWithPodcast(
    @Embedded val episode: EpisodeEntity,
    @ColumnInfo(name = "podcast_title") val podcastTitle: String,
    @ColumnInfo(name = "podcast_artwork_url") val podcastArtworkUrl: String?,
)

fun PodcastEntity.toDomain() = Podcast(
    id = id,
    feedUrl = feedUrl,
    title = title,
    author = author,
    description = description,
    artworkUrl = artworkUrl,
    link = link,
    categories = categories.split('\n').filter { it.isNotBlank() },
    subscribedAt = Instant.ofEpochMilli(subscribedAt),
    lastRefreshedAt = lastRefreshedAt?.let(Instant::ofEpochMilli),
    lastRefreshError = lastRefreshError,
)

fun EpisodeWithPodcast.toDomain() = Episode(
    id = episode.id,
    podcastId = episode.podcastId,
    podcastTitle = podcastTitle,
    title = episode.title,
    description = episode.description,
    audioUrl = episode.audioUrl,
    durationSeconds = episode.durationSeconds,
    publishedAt = episode.publishedAt?.let(Instant::ofEpochMilli),
    artworkUrl = episode.artworkUrl ?: podcastArtworkUrl,
    season = episode.season,
    episodeNumber = episode.episodeNumber,
    type = episode.type,
    playbackPositionMs = episode.playbackPositionMs,
    completed = episode.completed,
    downloadState = episode.downloadState,
    downloadProgress = episode.downloadProgress,
    localFilePath = episode.localFilePath,
)
