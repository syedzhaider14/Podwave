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
