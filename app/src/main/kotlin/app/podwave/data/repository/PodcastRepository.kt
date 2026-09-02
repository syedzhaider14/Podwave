package app.podwave.data.repository

import androidx.room.withTransaction
import app.podwave.data.db.PodcastEntity
import app.podwave.data.db.PodwaveDatabase
import app.podwave.data.db.toDomain
import app.podwave.data.remote.FeedFetcher
import app.podwave.data.remote.normalizeFeedUrl
import app.podwave.domain.Podcast
import app.podwave.rss.Opml
import app.podwave.rss.OpmlOutline
import app.podwave.rss.ParsedFeed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class RefreshSummary(val refreshed: Int, val failed: Int, val newEpisodes: Int)

data class ImportSummary(val added: Int, val alreadySubscribed: Int, val failed: List<String>)

class PodcastRepository(
    private val database: PodwaveDatabase,
    private val fetcher: FeedFetcher,
    private val now: () -> Long,
) {
    private val podcastDao = database.podcastDao()
    private val episodeDao = database.episodeDao()

    fun observeSubscriptions(): Flow<List<Podcast>> =
        podcastDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observePodcast(id: Long): Flow<Podcast?> = podcastDao.observe(id).map { it?.toDomain() }

    /** Subscribes to a feed, returning the podcast id. Re-subscribing returns the existing id. */
    suspend fun subscribe(feedUrlInput: String): Result<Long> = runCatchingCancellable {
        val feedUrl = normalizeFeedUrl(feedUrlInput)
        podcastDao.getByFeedUrl(feedUrl)?.let { return@runCatchingCancellable it.id }

        val feed = fetcher.fetch(feedUrl)
        val timestamp = now()
        database.withTransaction {
            val id = podcastDao.insert(feed.toEntity(feedUrl, timestamp))
            val merge = FeedMerger.merge(id, existing = emptyList(), parsed = feed.episodes, now = timestamp)
            episodeDao.insertAll(merge.toInsert)
            id
        }
    }

    /** Re-fetches one feed. Returns the number of new episodes. */
    suspend fun refresh(podcastId: Long): Result<Int> {
        val podcast = podcastDao.get(podcastId) ?: return Result.failure(NoSuchElementException("Podcast $podcastId"))
        val result = runCatchingCancellable {
            val feed = fetcher.fetch(podcast.feedUrl)
            database.withTransaction {
                podcastDao.update(
                    podcast.copy(
                        title = feed.title,
                        author = feed.author,
                        description = feed.description,
                        artworkUrl = feed.artworkUrl ?: podcast.artworkUrl,
                        link = feed.link,
                        categories = feed.categories.joinToString("\n"),
                    ),
                )
                val merge = FeedMerger.merge(podcastId, episodeDao.getForPodcast(podcastId), feed.episodes, now())
                val inserted = episodeDao.insertAll(merge.toInsert).count { it != -1L }
                if (merge.toUpdate.isNotEmpty()) episodeDao.updateAll(merge.toUpdate)
                inserted
            }
        }
        podcastDao.markRefreshed(podcastId, now(), result.exceptionOrNull()?.message)
        return result
    }

    /** Refreshes every subscription, a few at a time. */
    suspend fun refreshAll(parallelism: Int = 4): RefreshSummary = coroutineScope {
        val semaphore = Semaphore(parallelism)
        val results = podcastDao.getAll().map { podcast ->
            async { semaphore.withPermit { refresh(podcast.id) } }
        }.awaitAll()
        RefreshSummary(
            refreshed = results.count { it.isSuccess },
            failed = results.count { it.isFailure },
            newEpisodes = results.sumOf { it.getOrDefault(0) },
        )
    }

    /** Removes the podcast and its episodes. Returns downloaded files the caller should delete. */
    suspend fun unsubscribe(podcastId: Long): List<String> {
        val files = episodeDao.downloadedFiles(podcastId)
        podcastDao.delete(podcastId)
        return files
    }

    suspend fun exportOpml(): String =
        Opml.export(podcastDao.getAll().sortedBy { it.title.lowercase() }.map { OpmlOutline(it.title, it.feedUrl) })

    suspend fun import(outlines: List<OpmlOutline>): ImportSummary {
        var added = 0
        var existing = 0
        val failed = mutableListOf<String>()
        for (outline in outlines) {
            if (podcastDao.getByFeedUrl(normalizeFeedUrl(outline.feedUrl)) != null) {
                existing++
                continue
            }
            subscribe(outline.feedUrl).onSuccess { added++ }.onFailure { failed += outline.title }
        }
        return ImportSummary(added, existing, failed)
    }

    private fun ParsedFeed.toEntity(feedUrl: String, timestamp: Long) = PodcastEntity(
        feedUrl = feedUrl,
        title = title,
        author = author,
        description = description,
        artworkUrl = artworkUrl,
        link = link,
        categories = categories.joinToString("\n"),
        subscribedAt = timestamp,
        lastRefreshedAt = timestamp,
        lastRefreshError = null,
    )
}

internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
