package app.podwave.rss

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.Reader

/**
 * Streaming RSS 2.0 parser with support for the iTunes podcast and
 * `content:encoded` extensions.
 *
 * The XML parser is injected because Android provides one
 * (`android.util.Xml.newPullParser()`) while plain JVM tests use kxml2.
 * Streaming keeps memory flat even for feeds with thousands of episodes.
 */
class RssParser(private val newParser: () -> XmlPullParser) {

    fun parse(reader: Reader): ParsedFeed {
        val parser = newParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(reader)
        }
        try {
            parser.nextTag()
            if (parser.name != "rss") throw FeedParseException("Not an RSS feed (root element <${parser.name}>)")
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "channel" && parser.namespace.isEmpty()) {
                    return readChannel(parser)
                }
            }
            throw FeedParseException("Feed has no <channel>")
        } catch (e: XmlPullParserException) {
            throw FeedParseException("Malformed XML: ${e.message}", e)
        } catch (e: IOException) {
            throw FeedParseException("Couldn't read feed: ${e.message}", e)
        }
    }

    private fun readChannel(parser: XmlPullParser): ParsedFeed {
        var title: String? = null
        var link: String? = null
        var description: String? = null
        var itunesSummary: String? = null
        var author: String? = null
        var itunesImage: String? = null
        var rssImage: String? = null
        var language: String? = null
        var explicit = false
        val categories = mutableListOf<String>()
        val episodes = mutableListOf<ParsedEpisode>()

        forEachChild(parser) { ns, name ->
            when {
                ns.isEmpty() && name == "title" -> title = parser.readText()
                ns.isEmpty() && name == "link" -> link = parser.readText()
                ns.isEmpty() && name == "description" -> description = parser.readText()
                ns.isEmpty() && name == "language" -> language = parser.readText()
                ns.isEmpty() && name == "image" -> rssImage = readRssImage(parser)
                ns.isEmpty() && name == "item" -> readItem(parser)?.let(episodes::add)
                ns == ITUNES && name == "author" -> author = parser.readText()
                ns == ITUNES && name == "summary" -> itunesSummary = parser.readText()
                ns == ITUNES && name == "image" -> {
                    itunesImage = parser.getAttributeValue(null, "href")
                    parser.skip()
                }
                ns == ITUNES && name == "explicit" -> explicit = parser.readText().isExplicitFlag()
                ns == ITUNES && name == "category" -> categories += readCategories(parser)
                else -> parser.skip()
            }
        }

        val feedTitle = title?.takeIf { it.isNotBlank() } ?: throw FeedParseException("Feed has no title")
        return ParsedFeed(
            title = feedTitle,
            link = link?.takeIf { it.isNotBlank() },
            description = (description ?: itunesSummary)?.takeIf { it.isNotBlank() },
            author = author?.takeIf { it.isNotBlank() },
            artworkUrl = (itunesImage ?: rssImage)?.takeIf { it.isNotBlank() },
            language = language?.takeIf { it.isNotBlank() },
            categories = categories.distinct(),
            explicit = explicit,
            episodes = episodes.distinctBy { it.guid },
        )
    }

    /** Returns null for items without a playable enclosure (blog posts, announcements). */
    private fun readItem(parser: XmlPullParser): ParsedEpisode? {
        var guid: String? = null
        var title: String? = null
        var description: String? = null
        var contentEncoded: String? = null
        var itunesSummary: String? = null
        var link: String? = null
        var audioUrl: String? = null
        var audioType: String? = null
        var audioLength: Long? = null
        var duration: Long? = null
        var pubDate: String? = null
        var image: String? = null
        var season: Int? = null
        var episodeNumber: Int? = null
        var type = EpisodeType.Full
        var explicit = false

        forEachChild(parser) { ns, name ->
            when {
                ns.isEmpty() && name == "guid" -> guid = parser.readText()
                ns.isEmpty() && name == "title" -> title = parser.readText()
                ns.isEmpty() && name == "description" -> description = parser.readText()
                ns.isEmpty() && name == "link" -> link = parser.readText()
                ns.isEmpty() && name == "pubDate" -> pubDate = parser.readText()
                ns.isEmpty() && name == "enclosure" -> {
                    audioUrl = parser.getAttributeValue(null, "url")
                    audioType = parser.getAttributeValue(null, "type")
                    audioLength = parser.getAttributeValue(null, "length")?.trim()?.toLongOrNull()?.takeIf { it > 0 }
                    parser.skip()
                }
                ns == CONTENT && name == "encoded" -> contentEncoded = parser.readText()
                ns == ITUNES && name == "summary" -> itunesSummary = parser.readText()
                ns == ITUNES && name == "duration" -> duration = parseItunesDuration(parser.readText())
                ns == ITUNES && name == "image" -> {
                    image = parser.getAttributeValue(null, "href")
                    parser.skip()
                }
                ns == ITUNES && name == "season" -> season = parser.readText().trim().toIntOrNull()
                ns == ITUNES && name == "episode" -> episodeNumber = parser.readText().trim().toIntOrNull()
                ns == ITUNES && name == "episodeType" -> type = when (parser.readText().trim().lowercase()) {
                    "trailer" -> EpisodeType.Trailer
                    "bonus" -> EpisodeType.Bonus
                    else -> EpisodeType.Full
                }
                ns == ITUNES && name == "explicit" -> explicit = parser.readText().isExplicitFlag()
                ns == ITUNES && name == "title" && title == null -> title = parser.readText()
                else -> parser.skip()
            }
        }

        val url = audioUrl?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return null
        return ParsedEpisode(
            guid = guid?.trim()?.takeIf { it.isNotEmpty() } ?: url,
            title = title?.trim()?.takeIf { it.isNotEmpty() } ?: "Untitled episode",
            description = (contentEncoded ?: description ?: itunesSummary)?.trim()?.takeIf { it.isNotEmpty() },
            link = link?.trim()?.takeIf { it.isNotEmpty() },
            audioUrl = url,
            audioType = audioType,
            audioLengthBytes = audioLength,
            durationSeconds = duration,
            publishedAt = Rfc822Dates.parse(pubDate),
            artworkUrl = image?.trim()?.takeIf { it.isNotEmpty() },
            season = season,
            episode = episodeNumber,
            type = type,
            explicit = explicit,
        )
    }

    private fun readRssImage(parser: XmlPullParser): String? {
        var url: String? = null
        forEachChild(parser) { ns, name ->
            if (ns.isEmpty() && name == "url") url = parser.readText() else parser.skip()
        }
        return url
    }

    /** iTunes categories nest: <itunes:category text="Society"><itunes:category text="Philosophy"/></...> */
    private fun readCategories(parser: XmlPullParser): List<String> {
        val result = mutableListOf<String>()
        parser.getAttributeValue(null, "text")?.takeIf { it.isNotBlank() }?.let(result::add)
        forEachChild(parser) { ns, name ->
            if (ns == ITUNES && name == "category") result += readCategories(parser) else parser.skip()
        }
        return result
    }

    companion object {
        const val ITUNES = "http://www.itunes.com/dtds/podcast-1.0.dtd"
        const val CONTENT = "http://purl.org/rss/1.0/modules/content/"
    }
}

/** Calls [block] for each direct child element; [block] must consume the element it is given. */
internal inline fun forEachChild(parser: XmlPullParser, block: (namespace: String, name: String) -> Unit) {
    val depth = parser.depth
    while (true) {
        val event = parser.next()
        if (event == XmlPullParser.END_DOCUMENT) return
        if (event == XmlPullParser.END_TAG && parser.depth == depth) return
        if (event == XmlPullParser.START_TAG && parser.depth == depth + 1) {
            block(parser.namespace.orEmpty(), parser.name)
        }
    }
}

/** Reads the text content of the current element, including CDATA, and moves to its end tag. */
internal fun XmlPullParser.readText(): String {
    val builder = StringBuilder()
    val depth = depth
    while (true) {
        when (next()) {
            XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> builder.append(text.orEmpty())
            XmlPullParser.START_TAG -> skip()
            XmlPullParser.END_TAG -> if (this.depth == depth) return builder.toString().trim()
            XmlPullParser.END_DOCUMENT -> return builder.toString().trim()
        }
    }
}

/** Skips the current element and all its children. */
internal fun XmlPullParser.skip() {
    if (eventType != XmlPullParser.START_TAG) return
    var level = 1
    while (level > 0) {
        when (next()) {
            XmlPullParser.START_TAG -> level++
            XmlPullParser.END_TAG -> level--
            XmlPullParser.END_DOCUMENT -> return
        }
    }
}

private fun String.isExplicitFlag(): Boolean = trim().lowercase() in setOf("yes", "true", "explicit")
