package app.podwave.rss

import java.time.Instant

/** A podcast feed as parsed from RSS, before it touches the database. */
data class ParsedFeed(
    val title: String,
    val link: String?,
    val description: String?,
    val author: String?,
    val artworkUrl: String?,
    val language: String?,
    val categories: List<String>,
    val explicit: Boolean,
    val episodes: List<ParsedEpisode>,
)

data class ParsedEpisode(
    /** Stable identity: the RSS guid, or the enclosure URL when a feed omits guids. */
    val guid: String,
    val title: String,
    val description: String?,
    val link: String?,
    val audioUrl: String,
    val audioType: String?,
    val audioLengthBytes: Long?,
    val durationSeconds: Long?,
    val publishedAt: Instant?,
    val artworkUrl: String?,
    val season: Int?,
    val episode: Int?,
    val type: EpisodeType,
    val explicit: Boolean,
)

enum class EpisodeType { Full, Trailer, Bonus }

class FeedParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
