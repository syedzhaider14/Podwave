package dev.podwave.data

import dev.podwave.data.db.EpisodeDao
import dev.podwave.data.db.QueueDao
import dev.podwave.data.db.asModel
import dev.podwave.model.Episode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

interface EpisodeRepository {
    fun observeForPodcast(feedUrl: String): Flow<List<Episode>>

    fun observeEpisode(id: String): Flow<Episode?>

    fun observeInProgress(limit: Int = 10): Flow<List<Episode>>

    fun observeLatest(limit: Int = 30): Flow<List<Episode>>

    fun observeDownloaded(): Flow<List<Episode>>

    fun observeQueue(): Flow<List<Episode>>

    suspend fun getEpisode(id: String): Episode?

    suspend fun getEpisodes(ids: List<String>): List<Episode>

    suspend fun savePosition(id: String, positionMs: Long)

    suspend fun markPlayed(id: String)

    suspend fun markUnplayed(id: String)

    suspend fun queueIds(): List<String>

    suspend fun addToQueue(id: String, playNext: Boolean)

    suspend fun removeFromQueue(id: String)

    suspend fun moveInQueue(from: Int, to: Int)

    suspend fun clearQueue()
}

@Singleton
class DefaultEpisodeRepository @Inject constructor(
    private val episodeDao: EpisodeDao,
    private val queueDao: QueueDao,
    private val clock: Clock,
) : EpisodeRepository {

    override fun observeForPodcast(feedUrl: String) =
        episodeDao.observeForPodcast(feedUrl).map { list -> list.map { it.asModel() } }

    override fun observeEpisode(id: String) = episodeDao.observe(id).map { it?.asModel() }

    override fun observeInProgress(limit: Int) =
        episodeDao.observeInProgress(limit).map { list -> list.map { it.asModel() } }

    override fun observeLatest(limit: Int) =
        episodeDao.observeLatestUnplayed(limit).map { list -> list.map { it.asModel() } }

    override fun observeDownloaded() = episodeDao.observeDownloaded().map { list -> list.map { it.asModel() } }

    override fun observeQueue() = queueDao.observe().map { list -> list.map { it.asModel() } }

    override suspend fun getEpisode(id: String) = episodeDao.get(id)?.asModel()

    override suspend fun getEpisodes(ids: List<String>): List<Episode> {
        if (ids.isEmpty()) return emptyList()
        val byId = episodeDao.getAll(ids).associateBy { it.episode.id }
        return ids.mapNotNull { byId[it]?.asModel() }
    }

    override suspend fun savePosition(id: String, positionMs: Long) =
        episodeDao.updatePosition(id, positionMs.coerceAtLeast(0), clock.instant())

    override suspend fun markPlayed(id: String) {
        episodeDao.markPlayed(id, clock.instant())
        queueDao.remove(id)
    }

    override suspend fun markUnplayed(id: String) = episodeDao.markUnplayed(id)

    override suspend fun queueIds(): List<String> = queueDao.getIds()

    override suspend fun addToQueue(id: String, playNext: Boolean) =
        if (playNext) queueDao.addFirst(id) else queueDao.addLast(id)

    override suspend fun removeFromQueue(id: String) = queueDao.remove(id)

    override suspend fun moveInQueue(from: Int, to: Int) {
        queueDao.replaceOrder(QueueOrder.move(queueDao.getIds(), from, to))
    }

    override suspend fun clearQueue() = queueDao.clear()
}

object QueueOrder {
    /** Moves the item at [from] to [to], clamping out-of-range indices instead of throwing. */
    fun <T> move(items: List<T>, from: Int, to: Int): List<T> {
        if (from !in items.indices) return items
        val target = to.coerceIn(0, items.lastIndex)
        if (from == target) return items
        return items.toMutableList().apply { add(target, removeAt(from)) }
    }
}
