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
