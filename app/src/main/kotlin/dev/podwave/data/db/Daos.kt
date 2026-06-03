package dev.podwave.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.podwave.model.DownloadStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

private const val EPISODE_WITH_PODCAST = """
    SELECT e.*, p.title AS podcast_title, p.artwork_url AS podcast_artwork_url
    FROM episodes e JOIN podcasts p ON p.feed_url = e.podcast_feed_url
"""

@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcasts WHERE subscribed = 1 ORDER BY title COLLATE NOCASE")
    fun observeSubscribed(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts WHERE subscribed = 1")
    suspend fun getSubscribed(): List<PodcastEntity>

    @Query("SELECT * FROM podcasts WHERE feed_url = :feedUrl")
    fun observe(feedUrl: String): Flow<PodcastEntity?>

    @Query("SELECT * FROM podcasts WHERE feed_url = :feedUrl")
    suspend fun get(feedUrl: String): PodcastEntity?

    @Upsert
    suspend fun upsert(podcast: PodcastEntity)

    @Query("UPDATE podcasts SET subscribed = :subscribed, subscribed_at = :at WHERE feed_url = :feedUrl")
    suspend fun setSubscribed(feedUrl: String, subscribed: Boolean, at: Instant?)
}

@Dao
interface EpisodeDao {
    @Query("$EPISODE_WITH_PODCAST WHERE e.podcast_feed_url = :feedUrl ORDER BY e.published_at DESC")
    fun observeForPodcast(feedUrl: String): Flow<List<EpisodeWithPodcast>>

    @Query("$EPISODE_WITH_PODCAST WHERE e.id = :id")
    fun observe(id: String): Flow<EpisodeWithPodcast?>

    @Query("$EPISODE_WITH_PODCAST WHERE e.id = :id")
    suspend fun get(id: String): EpisodeWithPodcast?

    @Query("$EPISODE_WITH_PODCAST WHERE e.id IN (:ids)")
    suspend fun getAll(ids: List<String>): List<EpisodeWithPodcast>

    /** Started but not finished, most recently played first: the "Continue listening" row. */
    @Query(
        "$EPISODE_WITH_PODCAST WHERE e.played_at IS NULL AND e.position_ms > 0 " +
            "ORDER BY e.last_played_at DESC LIMIT :limit",
    )
    fun observeInProgress(limit: Int): Flow<List<EpisodeWithPodcast>>

    @Query(
        "$EPISODE_WITH_PODCAST WHERE p.subscribed = 1 AND e.played_at IS NULL " +
            "ORDER BY e.published_at DESC LIMIT :limit",
    )
    fun observeLatestUnplayed(limit: Int): Flow<List<EpisodeWithPodcast>>

    @Query("$EPISODE_WITH_PODCAST WHERE e.download_status = 'Downloaded' ORDER BY e.published_at DESC")
    fun observeDownloaded(): Flow<List<EpisodeWithPodcast>>

    @Query("SELECT * FROM episodes WHERE podcast_feed_url = :feedUrl")
    suspend fun getEntitiesForPodcast(feedUrl: String): List<EpisodeEntity>

    @Upsert
    suspend fun upsertAll(episodes: List<EpisodeEntity>)

    @Query("UPDATE episodes SET position_ms = :positionMs, last_played_at = :at WHERE id = :id")
    suspend fun updatePosition(id: String, positionMs: Long, at: Instant)

    @Query("UPDATE episodes SET played_at = :at, position_ms = 0, last_played_at = :at WHERE id = :id")
    suspend fun markPlayed(id: String, at: Instant)

    @Query("UPDATE episodes SET played_at = NULL WHERE id = :id")
    suspend fun markUnplayed(id: String)

    @Query(
        "UPDATE episodes SET download_status = :status, download_progress = :progress, " +
            "local_file_path = :path WHERE id = :id",
    )
    suspend fun updateDownload(id: String, status: DownloadStatus, progress: Int, path: String?)
}

@Dao
interface QueueDao {
    @Query(
        "SELECT e.*, p.title AS podcast_title, p.artwork_url AS podcast_artwork_url " +
            "FROM queue q JOIN episodes e ON e.id = q.episode_id " +
            "JOIN podcasts p ON p.feed_url = e.podcast_feed_url ORDER BY q.sort_order",
    )
    fun observe(): Flow<List<EpisodeWithPodcast>>

    @Query("SELECT episode_id FROM queue ORDER BY sort_order")
    suspend fun getIds(): List<String>

    @Query("SELECT MIN(sort_order) FROM queue")
    suspend fun minOrder(): Long?

    @Query("SELECT MAX(sort_order) FROM queue")
    suspend fun maxOrder(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: QueueEntity)

    @Query("DELETE FROM queue WHERE episode_id = :episodeId")
    suspend fun remove(episodeId: String)

    @Query("DELETE FROM queue")
    suspend fun clear()

    @Insert
    suspend fun insertAll(items: List<QueueEntity>)

    /** Rewrites the whole order in one transaction; queues are small so this stays cheap. */
    @Transaction
    suspend fun replaceOrder(episodeIds: List<String>) {
        clear()
        insertAll(episodeIds.mapIndexed { index, id -> QueueEntity(id, index.toLong()) })
    }

    @Transaction
    suspend fun addFirst(episodeId: String) {
        remove(episodeId)
        insert(QueueEntity(episodeId, (minOrder() ?: 0L) - 1))
    }

    @Transaction
    suspend fun addLast(episodeId: String) {
        remove(episodeId)
        insert(QueueEntity(episodeId, (maxOrder() ?: -1L) + 1))
    }
}
