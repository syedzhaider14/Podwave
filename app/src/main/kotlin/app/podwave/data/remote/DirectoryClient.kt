package app.podwave.data.remote

import app.podwave.domain.DirectoryPodcast
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

/**
 * Podcast directory search backed by the public iTunes Search API.
 * https://performance-partners.apple.com/search-api
 */
class DirectoryClient(private val http: HttpClient, private val baseUrl: String = BASE_URL) {

    suspend fun search(term: String, limit: Int = 30): List<DirectoryPodcast> {
        val query = term.trim()
        if (query.isEmpty()) return emptyList()
        val response: ITunesResponse = http.get("$baseUrl/search") {
            parameter("media", "podcast")
            parameter("entity", "podcast")
            parameter("term", query)
            parameter("limit", limit)
        }.body()
        return response.results.mapNotNull { it.toDirectoryPodcast() }.distinctBy { it.feedUrl }
    }

    companion object {
        const val BASE_URL = "https://itunes.apple.com"
    }
}

@Serializable
internal data class ITunesResponse(
    val resultCount: Int = 0,
    val results: List<ITunesPodcast> = emptyList(),
)

@Serializable
internal data class ITunesPodcast(
    val collectionName: String? = null,
    val artistName: String? = null,
    val feedUrl: String? = null,
    val artworkUrl600: String? = null,
    val artworkUrl100: String? = null,
    val primaryGenreName: String? = null,
    val trackCount: Int? = null,
) {
    /** Entries without a feed URL can't be subscribed to, so they're dropped. */
    fun toDirectoryPodcast(): DirectoryPodcast? {
        val url = feedUrl?.takeIf { it.isNotBlank() } ?: return null
        val title = collectionName?.takeIf { it.isNotBlank() } ?: return null
        return DirectoryPodcast(
            title = title,
            author = artistName,
            feedUrl = url,
            artworkUrl = artworkUrl600 ?: artworkUrl100,
            genre = primaryGenreName,
            episodeCount = trackCount,
        )
    }
}
