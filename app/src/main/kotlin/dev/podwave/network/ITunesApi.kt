package dev.podwave.network

import dev.podwave.model.DirectoryPodcast
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
data class ITunesSearchResponse(
    val resultCount: Int = 0,
    val results: List<ITunesPodcastDto> = emptyList(),
)

@Serializable
data class ITunesPodcastDto(
    val collectionId: Long,
    val collectionName: String = "",
    val artistName: String = "",
    val feedUrl: String? = null,
    val artworkUrl600: String? = null,
    val artworkUrl100: String? = null,
    val primaryGenreName: String? = null,
)

@Serializable
data class TopChartResponse(val feed: TopChartFeed)

@Serializable
data class TopChartFeed(val results: List<TopChartEntry> = emptyList())

@Serializable
data class TopChartEntry(
    val id: String,
    val name: String = "",
    val artistName: String = "",
    @SerialName("artworkUrl100") val artworkUrl: String? = null,
    val genres: List<TopChartGenre> = emptyList(),
)

@Serializable
data class TopChartGenre(val name: String = "")

/**
 * Apple's public podcast directory. Search and lookup return feed URLs; the top chart does not,
 * so its entries are resolved with a lookup when the user opens one.
 */
class ITunesApi @Inject constructor(private val client: HttpClient) {

    suspend fun search(term: String, limit: Int = 30): List<DirectoryPodcast> =
        client.get("https://itunes.apple.com/search") {
            parameter("media", "podcast")
            parameter("entity", "podcast")
            parameter("term", term)
            parameter("limit", limit)
        }.body<ITunesSearchResponse>().results.mapNotNull { it.toDirectoryPodcast() }

    suspend fun lookup(itunesId: Long): DirectoryPodcast? =
        client.get("https://itunes.apple.com/lookup") {
            parameter("id", itunesId)
            parameter("entity", "podcast")
        }.body<ITunesSearchResponse>().results.firstOrNull()?.toDirectoryPodcast()

    suspend fun topPodcasts(country: String = "us", limit: Int = 25): List<DirectoryPodcast> =
        client.get("https://rss.marketingtools.apple.com/api/v2/$country/podcasts/top/$limit/podcasts.json")
            .body<TopChartResponse>()
            .feed.results
            .mapNotNull { entry ->
                entry.id.toLongOrNull()?.let { id ->
                    DirectoryPodcast(
                        itunesId = id,
                        title = entry.name,
                        author = entry.artistName,
                        artworkUrl = entry.artworkUrl?.let(::upscaleArtwork),
                        feedUrl = null,
                        genre = entry.genres.firstOrNull()?.name?.takeIf { it != "Podcasts" },
                    )
                }
            }
}

internal fun ITunesPodcastDto.toDirectoryPodcast(): DirectoryPodcast? {
    if (collectionName.isBlank()) return null
    return DirectoryPodcast(
        itunesId = collectionId,
        title = collectionName,
        author = artistName,
        artworkUrl = artworkUrl600 ?: artworkUrl100?.let(::upscaleArtwork),
        feedUrl = feedUrl?.takeIf { it.startsWith("http") },
        genre = primaryGenreName,
    )
}

/** Apple serves any size by rewriting the dimensions in the URL: `.../100x100bb.jpg` → `600x600`. */
internal fun upscaleArtwork(url: String): String = url.replace(Regex("""/\d+x\d+(bb)?\."""), "/600x600bb.")
