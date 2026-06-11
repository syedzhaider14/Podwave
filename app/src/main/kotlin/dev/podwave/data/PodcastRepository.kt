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

    override fun observePodcast(feedUrl: String): Flow<Podcast?> =
        podcastDao.observe(feedUrl).map { it?.asModel() }

    override suspend fun refresh(feedUrl: String, maxAge: Duration): Result<RefreshOutcome> {
        val lock = synchronized(refreshLocks) { refreshLocks.getOrPut(feedUrl) { Mutex() } }
        return lock.withLock { refreshLocked(feedUrl, maxAge) }
    }

    private suspend fun refreshLocked(feedUrl: String, maxAge: Duration): Result<RefreshOutcome> {
        val now = clock.instant()
        val existing = podcastDao.get(feedUrl)
        val lastRefresh = existing?.lastRefreshedAt
        if (lastRefresh != null && Duration.between(lastRefresh, now) < maxAge) {
            return Result.success(RefreshOutcome(emptyList()))
        }

        return try {
            when (val result = feedFetcher.fetch(feedUrl, etag = existing?.etag)) {
                FeedResult.NotModified -> {
                    existing?.let { podcastDao.upsert(it.copy(lastRefreshedAt = now)) }
                    Result.success(RefreshOutcome(emptyList()))
                }
                is FeedResult.Updated -> {
                    val feed = result.feed
                    podcastDao.upsert(
                        PodcastEntity(
                            feedUrl = feedUrl,
                            title = feed.title,
                            author = feed.author,
                            description = feed.description,
                            artworkUrl = feed.artworkUrl,
                            websiteUrl = feed.websiteUrl,
                            categories = feed.categories,
                            subscribed = existing?.subscribed ?: false,
                            subscribedAt = existing?.subscribedAt,
                            lastRefreshedAt = now,
                            etag = result.etag,
                        ),
                    )
                    val merge = FeedMerger.merge(
                        feedUrl = feedUrl,
                        parsed = feed.episodes,
                        existing = episodeDao.getEntitiesForPodcast(feedUrl),
                        now = now,
                    )
                    episodeDao.upsertAll(merge.episodes)
                    Result.success(
                        RefreshOutcome(FeedMerger.newEpisodesToAnnounce(merge, isFirstFetch = existing == null)),
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun refreshSubscriptions(): Map<String, Result<RefreshOutcome>> =
        podcastDao.getSubscribed().associate { it.feedUrl to refresh(it.feedUrl) }

    override suspend fun setSubscribed(feedUrl: String, subscribed: Boolean) {
        podcastDao.setSubscribed(feedUrl, subscribed, if (subscribed) clock.instant() else null)
    }
}
