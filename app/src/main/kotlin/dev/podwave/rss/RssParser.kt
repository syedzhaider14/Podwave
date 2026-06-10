package dev.podwave.rss

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.time.Instant

data class ParsedFeed(
    val title: String,
    val author: String,
    val description: String,
    val artworkUrl: String?,
    val websiteUrl: String?,
    val categories: List<String>,
    val episodes: List<ParsedEpisode>,
)

data class ParsedEpisode(
    val guid: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val audioMimeType: String?,
    val audioSizeBytes: Long?,
    val durationMs: Long?,
    val publishedAt: Instant?,
    val artworkUrl: String?,
    val season: Int?,
    val episodeNumber: Int?,
    val explicit: Boolean,
)

class FeedParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A streaming RSS 2.0 parser that understands the iTunes and `content:` namespaces.
 *
 * It takes a parser factory so production code can use Android's built-in parser while JVM
 * unit tests use kXML. Items without an audio enclosure (blog posts, trailers with video only)
 * are skipped because they cannot be played.
 */
class RssParser(
    private val newParser: () -> XmlPullParser,
) {
    fun parse(reader: Reader): ParsedFeed = parseWith { setInput(reader) }

    /** Lets the parser honour the encoding declared in the XML prolog (some feeds are not UTF-8). */
    fun parse(input: InputStream): ParsedFeed = parseWith { setInput(input, null) }

    private fun parseWith(setInput: XmlPullParser.() -> Unit): ParsedFeed {
        val parser = newParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput()
        }
        return try {
            readDocument(parser)
        } catch (error: XmlPullParserException) {
            throw FeedParseException("Malformed feed: ${error.message}", error)
        } catch (error: IOException) {
            throw FeedParseException("Could not read feed", error)
        }
    }

    private fun readDocument(parser: XmlPullParser): ParsedFeed {
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "channel" && parser.namespace.isEmpty()) {
                return readChannel(parser)
            }
        }
        throw FeedParseException("No <channel> element found; is this an RSS feed?")
    }

    private fun readChannel(parser: XmlPullParser): ParsedFeed {
        var title = ""
        var author = ""
        var description = ""
        var itunesSummary = ""
        var itunesImage: String? = null
        var rssImage: String? = null
        var link: String? = null
        val categories = linkedSetOf<String>()
        val episodes = mutableListOf<ParsedEpisode>()

        val depth = parser.depth
        while (!(parser.next() == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (parser.eventType == XmlPullParser.END_DOCUMENT) {
                throw FeedParseException("Feed ended before </channel>; the download may be truncated")
            }
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.namespace to parser.name) {
                NO_NS to "title" -> title = parser.readText()
                NO_NS to "description" -> description = parser.readText()
                NO_NS to "link" -> link = parser.readText().ifBlank { null }
                NO_NS to "image" -> rssImage = parser.readImageUrl()
                NO_NS to "item" -> parser.readItem(itunesImage)?.let(episodes::add)
                ITUNES to "author" -> author = parser.readText()
                ITUNES to "summary" -> itunesSummary = parser.readText()
                ITUNES to "image" -> itunesImage = parser.getAttributeValue(null, "href")?.trim().also { parser.skip() }
                ITUNES to "category" -> categories += parser.readCategories()
                else -> parser.skip()
            }
        }

        return ParsedFeed(
            title = title.ifBlank { "Untitled podcast" },
            author = author,
            description = description.ifBlank { itunesSummary },
            artworkUrl = itunesImage ?: rssImage,
            websiteUrl = link,
            categories = categories.toList(),
            episodes = episodes,
        )
    }

    private fun XmlPullParser.readItem(channelArtwork: String?): ParsedEpisode? {
        var guid: String? = null
        var title = ""
        var description = ""
        var contentEncoded = ""
        var itunesSummary = ""
        var enclosureUrl: String? = null
        var enclosureType: String? = null
        var enclosureLength: Long? = null
        var duration: String? = null
        var pubDate: String? = null
        var image: String? = null
        var season: Int? = null
        var episodeNumber: Int? = null
        var explicit: String? = null

        val depth = depth
        while (!(next() == XmlPullParser.END_TAG && this.depth == depth)) {
            if (eventType == XmlPullParser.END_DOCUMENT) break
            if (eventType != XmlPullParser.START_TAG) continue
            when (namespace to name) {
                NO_NS to "guid" -> guid = readText().ifBlank { null }
                NO_NS to "title" -> title = readText()
                NO_NS to "description" -> description = readText()
                NO_NS to "pubDate" -> pubDate = readText()
                NO_NS to "enclosure" -> {
                    enclosureUrl = getAttributeValue(null, "url")?.trim()
                    enclosureType = getAttributeValue(null, "type")?.trim()
                    enclosureLength = getAttributeValue(null, "length")?.trim()?.toLongOrNull()?.takeIf { it > 0 }
                    skip()
                }
                CONTENT to "encoded" -> contentEncoded = readText()
                ITUNES to "summary" -> itunesSummary = readText()
                ITUNES to "duration" -> duration = readText()
                ITUNES to "image" -> image = getAttributeValue(null, "href")?.trim().also { skip() }
                ITUNES to "season" -> season = readText().toIntOrNull()
                ITUNES to "episode" -> episodeNumber = readText().toIntOrNull()
                ITUNES to "explicit" -> explicit = readText()
                ITUNES to "title" -> if (title.isBlank()) title = readText() else skip()
                else -> skip()
            }
        }

        val audioUrl = enclosureUrl?.takeIf { it.startsWith("http") } ?: return null
        if (enclosureType != null && !enclosureType.startsWith("audio") && !audioUrl.looksLikeAudio()) return null

        return ParsedEpisode(
            guid = guid ?: audioUrl,
            title = title.ifBlank { "Untitled episode" },
            description = contentEncoded.ifBlank { description.ifBlank { itunesSummary } },
            audioUrl = audioUrl,
            audioMimeType = enclosureType,
            audioSizeBytes = enclosureLength,
            durationMs = parseDurationMs(duration),
            publishedAt = parsePubDate(pubDate),
            artworkUrl = image ?: channelArtwork,
            season = season,
            episodeNumber = episodeNumber,
            explicit = parseExplicit(explicit),
        )
    }

    private fun XmlPullParser.readImageUrl(): String? {
        var url: String? = null
        val depth = depth
        while (!(next() == XmlPullParser.END_TAG && this.depth == depth)) {
            if (eventType == XmlPullParser.END_DOCUMENT) break
            if (eventType == XmlPullParser.START_TAG) {
                if (name == "url") url = readText().ifBlank { null } else skip()
            }
        }
        return url
    }

    /** `<itunes:category text="Society &amp; Culture"><itunes:category text="Documentary"/>` */
    private fun XmlPullParser.readCategories(): List<String> {
        val result = mutableListOf<String>()
        getAttributeValue(null, "text")?.trim()?.takeIf { it.isNotEmpty() }?.let(result::add)
        val depth = depth
        while (!(next() == XmlPullParser.END_TAG && this.depth == depth)) {
            if (eventType == XmlPullParser.END_DOCUMENT) break
            if (eventType == XmlPullParser.START_TAG && namespace == ITUNES && name == "category") {
                result += readCategories()
            }
        }
        return result
    }

    private fun XmlPullParser.readText(): String {
        val builder = StringBuilder()
        val depth = depth
        while (!(next() == XmlPullParser.END_TAG && this.depth == depth)) {
            when (eventType) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> builder.append(text)
                XmlPullParser.START_TAG -> skip()
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return builder.toString().trim()
    }

    private fun XmlPullParser.skip() {
        if (eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth != 0) {
            when (next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private fun String.looksLikeAudio(): Boolean {
        val path = substringBefore('?').lowercase()
        return AUDIO_EXTENSIONS.any { path.endsWith(it) }
    }

    private companion object {
        const val NO_NS = ""
        const val ITUNES = "http://www.itunes.com/dtds/podcast-1.0.dtd"
        const val CONTENT = "http://purl.org/rss/1.0/modules/content/"
        val AUDIO_EXTENSIONS = listOf(".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav")
    }
}
