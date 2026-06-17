package dev.podwave.testing

import dev.podwave.data.PodcastRepository
import dev.podwave.data.RefreshOutcome
import dev.podwave.model.DirectoryPodcast
import dev.podwave.model.Podcast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Duration

class FakePodcastRepository : PodcastRepository {
    val subscriptions = MutableStateFlow<List<Podcast>>(emptyList())
    var chart: List<DirectoryPodcast> = emptyList()
    var searchResults: (String) -> List<DirectoryPodcast> = { emptyList() }
    var failure: Exception? = null

    val searchedTerms = mutableListOf<String>()
    var chartRequests = 0
        private set

    override suspend fun search(term: String): List<DirectoryPodcast> {
        searchedTerms += term
        failure?.let { throw it }
        return searchResults(term)
    }

    override suspend fun topPodcasts(): List<DirectoryPodcast> {
        chartRequests++
        failure?.let { throw it }
        return chart
    }

    override suspend fun resolveFeedUrl(podcast: DirectoryPodcast): String? = podcast.feedUrl

    override fun observeSubscriptions(): Flow<List<Podcast>> = subscriptions

    override fun observePodcast(feedUrl: String): Flow<Podcast?> =
        subscriptions.map { list -> list.firstOrNull { it.feedUrl == feedUrl } }

    override suspend fun refresh(feedUrl: String, maxAge: Duration): Result<RefreshOutcome> =
        Result.success(RefreshOutcome(emptyList()))

    override suspend fun refreshSubscriptions(): Map<String, Result<RefreshOutcome>> =
        subscriptions.value.associate { it.feedUrl to Result.success(RefreshOutcome(emptyList())) }

    override suspend fun setSubscribed(feedUrl: String, subscribed: Boolean) {
        subscriptions.update { list -> list.map { if (it.feedUrl == feedUrl) it.copy(subscribed = subscribed) else it } }
    }
}

fun directoryPodcast(id: Long, title: String = "Show $id", feedUrl: String? = "https://feeds.example.com/$id") =
    DirectoryPodcast(itunesId = id, title = title, author = "Author $id", artworkUrl = null, feedUrl = feedUrl, genre = null)
