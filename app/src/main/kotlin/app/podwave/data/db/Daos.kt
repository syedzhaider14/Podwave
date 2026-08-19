package app.podwave.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.podwave.domain.DownloadState
import kotlinx.coroutines.flow.Flow

private const val EPISODE_COLUMNS =
    "e.*, p.title AS podcast_title, p.artwork_url AS podcast_artwork_url"

@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcasts ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts WHERE id = :id")
    fun observe(id: Long): Flow<PodcastEntity?>

    @Query("SELECT * FROM podcasts WHERE id = :id")
    suspend fun get(id: Long): PodcastEntity?

    @Query("SELECT * FROM podcasts WHERE feed_url = :feedUrl")
    suspend fun getByFeedUrl(feedUrl: String): PodcastEntity?

    @Query("SELECT * FROM podcasts")
    suspend fun getAll(): List<PodcastEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(podcast: PodcastEntity): Long

    @Update
    suspend fun update(podcast: PodcastEntity)

    @Query("UPDATE podcasts SET last_refreshed_at = :at, last_refresh_error = :error WHERE id = :id")
    suspend fun markRefreshed(id: Long, at: Long, error: String?)

    @Query("DELETE FROM podcasts WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface EpisodeDao {
    @Query(
        "SELECT $EPISODE_COLUMNS FROM episodes e JOIN podcasts p ON p.id = e.podcast_id " +
            "WHERE e.podcast_id = :podcastId ORDER BY e.published_at DESC, e.id DESC",
    )
    fun observeForPodcast(podcastId: Long): Flow<List<EpisodeWithPodcast>>

    @Query(
        "SELECT $EPISODE_COLUMNS FROM episodes e JOIN podcasts p ON p.id = e.podcast_id " +
            "ORDER BY e.published_at DESC, e.id DESC LIMIT :limit",
    )
    fun observeLatest(limit: Int): Flow<List<EpisodeWithPodcast>>

    @Query(
        "SELECT $EPISODE_COLUMNS FROM episodes e JOIN podcasts p ON p.id = e.podcast_id " +
            "WHERE e.completed = 0 AND e.playback_position_ms > 0 ORDER BY e.last_played_at DESC",
    )
    fun observeInProgress(): Flow<List<EpisodeWithPodcast>>

    @Query(
        "SELECT $EPISODE_COLUMNS FROM episodes e JOIN podcasts p ON p.id = e.podcast_id " +
            "WHERE e.download_state != 'None' ORDER BY e.published_at DESC",
    )
    fun observeDownloads(): Flow<List<EpisodeWithPodcast>>

    @Query("SELECT $EPISODE_COLUMNS FROM episodes e JOIN podcasts p ON p.id = e.podcast_id WHERE e.id = :id")
    fun observe(id: Long): Flow<EpisodeWithPodcast?>

    @Query("SELECT $EPISODE_COLUMNS FROM episodes e JOIN podcasts p ON p.id = e.podcast_id WHERE e.id = :id")
    suspend fun get(id: Long): EpisodeWithPodcast?

    @Query("SELECT * FROM episodes WHERE podcast_id = :podcastId")
    suspend fun getForPodcast(podcastId: Long): List<EpisodeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(episodes: List<EpisodeEntity>): List<Long>

    @Update
    suspend fun updateAll(episodes: List<EpisodeEntity>)

    @Query(
        "UPDATE episodes SET playback_position_ms = :positionMs, completed = :completed, " +
            "last_played_at = :playedAt WHERE id = :id",
    )
    suspend fun updatePlayback(id: Long, positionMs: Long, completed: Boolean, playedAt: Long)

    @Query(
        "UPDATE episodes SET download_state = :state, download_progress = :progress, " +
            "local_file_path = :path WHERE id = :id",
    )
    suspend fun updateDownload(id: Long, state: DownloadState, progress: Int, path: String?)

    @Query("SELECT local_file_path FROM episodes WHERE podcast_id = :podcastId AND local_file_path IS NOT NULL")
    suspend fun downloadedFiles(podcastId: Long): List<String>
}

@Dao
interface QueueDao {
    @Query(
        "SELECT $EPISODE_COLUMNS FROM queue q " +
            "JOIN episodes e ON e.id = q.episode_id JOIN podcasts p ON p.id = e.podcast_id " +
            "ORDER BY q.position",
    )
    fun observeQueue(): Flow<List<EpisodeWithPodcast>>

    @Query("SELECT episode_id FROM queue ORDER BY position")
    suspend fun episodeIds(): List<Long>

    @Query("DELETE FROM queue")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<QueueEntryEntity>)

    /** Rewrites the queue so positions are always 0..n-1 with no gaps. */
    @Transaction
    suspend fun replace(ids: List<Long>) {
        clear()
        insertAll(ids.mapIndexed { index, id -> QueueEntryEntity(episodeId = id, position = index) })
    }
}
