package dev.podwave.data

import dev.podwave.data.db.EpisodeDao
import dev.podwave.data.db.PodcastDao
import dev.podwave.data.db.PodcastEntity
import dev.podwave.data.db.asModel
import dev.podwave.model.DirectoryPodcast
import dev.podwave.model.Podcast
import dev.podwave.network.FeedFetcher
import dev.podwave.network.FeedResult
import dev.podwave.network.ITunesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

data class RefreshOutcome(val newEpisodeIds: List<String>)

interface PodcastRepository {
    suspend fun search(term: String): List<DirectoryPodcast>

    suspend fun topPodcasts(): List<DirectoryPodcast>

    /** Directory entries from the chart have no feed URL; this looks it up. */
    suspend fun resolveFeedUrl(podcast: DirectoryPodcast): String?

    fun observeSubscriptions(): Flow<List<Podcast>>

    fun observePodcast(feedUrl: String): Flow<Podcast?>

    /** Fetches the feed if it has never been loaded or is older than [maxAge]. */
    suspend fun refresh(feedUrl: String, maxAge: Duration = Duration.ZERO): Result<RefreshOutcome>

    suspend fun refreshSubscriptions(): Map<String, Result<RefreshOutcome>>

    suspend fun setSubscribed(feedUrl: String, subscribed: Boolean)
}

@Singleton
class DefaultPodcastRepository @Inject constructor(
    private val itunes: ITunesApi,
    private val feedFetcher: FeedFetcher,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val clock: Clock,
) : PodcastRepository {

    /** One refresh per feed at a time: the worker and a pull-to-refresh can overlap. */
    private val refreshLocks = mutableMapOf<String, Mutex>()

    override suspend fun search(term: String) = itunes.search(term.trim())

    override suspend fun topPodcasts() = itunes.topPodcasts()

    override suspend fun resolveFeedUrl(podcast: DirectoryPodcast): String? =
        podcast.feedUrl ?: itunes.lookup(podcast.itunesId)?.feedUrl

    override fun observeSubscriptions(): Flow<List<Podcast>> =
        podcastDao.observeSubscribed().map { list -> list.map { it.asModel() } }

