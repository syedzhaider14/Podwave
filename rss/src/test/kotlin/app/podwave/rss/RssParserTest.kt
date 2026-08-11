package app.podwave.rss

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.StringReader
import java.time.Instant

class RssParserTest {
    private val parser = RssParser { KXmlParser() }

    private fun resource(name: String) = checkNotNull(javaClass.classLoader.getResourceAsStream(name)).reader()

    @Test
    fun `parses channel metadata`() {
        val feed = parser.parse(resource("feed_full.xml"))

        assertThat(feed.title).isEqualTo("The Signal & the Noise")
        assertThat(feed.link).isEqualTo("https://example.com/show")
        assertThat(feed.description).isEqualTo("Weekly conversations about <b>data</b>.")
        assertThat(feed.author).isEqualTo("Jane Host")
        assertThat(feed.artworkUrl).isEqualTo("https://example.com/art.jpg")
        assertThat(feed.language).isEqualTo("en-us")
        assertThat(feed.categories).containsExactly("Science", "Mathematics", "Technology").inOrder()
        assertThat(feed.explicit).isFalse()
    }

    @Test
    fun `parses episodes, skipping items without audio and duplicate guids`() {
        val episodes = parser.parse(resource("feed_full.xml")).episodes
        assertThat(episodes.map { it.guid }).containsExactly("ep-2", "https://cdn.example.com/trailer.mp3").inOrder()
    }

    @Test
    fun `parses a full episode`() {
        val episode = parser.parse(resource("feed_full.xml")).episodes.first()

        assertThat(episode.title).isEqualTo("Episode 2: Outliers")
        assertThat(episode.audioUrl).isEqualTo("https://cdn.example.com/ep2.mp3")
        assertThat(episode.audioType).isEqualTo("audio/mpeg")
        assertThat(episode.audioLengthBytes).isEqualTo(34_567_890L)
        assertThat(episode.durationSeconds).isEqualTo(3723L)
        assertThat(episode.publishedAt).isEqualTo(Instant.parse("2025-06-10T04:00:00Z"))
        assertThat(episode.season).isEqualTo(1)
        assertThat(episode.episode).isEqualTo(2)
        assertThat(episode.explicit).isTrue()
        assertThat(episode.type).isEqualTo(EpisodeType.Full)
        // content:encoded wins over the short description.
        assertThat(episode.description).isEqualTo("""<p>Full <a href="https://example.com">show notes</a>.</p>""")
    }

    @Test
    fun `falls back sensibly for sparse items`() {
        val trailer = parser.parse(resource("feed_full.xml")).episodes[1]

        assertThat(trailer.type).isEqualTo(EpisodeType.Trailer)
        assertThat(trailer.audioLengthBytes).isNull()
        assertThat(trailer.durationSeconds).isEqualTo(95L)
        assertThat(trailer.artworkUrl).isEqualTo("https://example.com/trailer.jpg")
        assertThat(trailer.description).isEqualTo("Coming soon.")
        assertThat(trailer.publishedAt).isEqualTo(Instant.parse("2025-06-02T16:30:00Z"))
    }

    @Test
    fun `rejects documents that are not RSS`() {
        val error = assertThrows(FeedParseException::class.java) {
            parser.parse(StringReader("""<html><body>Not a feed</body></html>"""))
        }
        assertThat(error).hasMessageThat().contains("Not an RSS feed")
    }

    @Test
    fun `rejects malformed XML`() {
        assertThrows(FeedParseException::class.java) {
            parser.parse(StringReader("""<rss><channel><title>Broken</channel></rss>"""))
        }
    }

    @Test
    fun `requires a channel title`() {
        val error = assertThrows(FeedParseException::class.java) {
            parser.parse(StringReader("""<rss version="2.0"><channel><link>x</link></channel></rss>"""))
        }
        assertThat(error).hasMessageThat().contains("no title")
    }

    @Test
    fun `uses the rss image when there is no itunes image`() {
        val feed = parser.parse(
            StringReader(
                """
                <rss version="2.0"><channel>
                  <title>Minimal</title>
                  <image><url>https://example.com/rss.png</url></image>
                </channel></rss>
                """.trimIndent(),
            ),
        )
        assertThat(feed.artworkUrl).isEqualTo("https://example.com/rss.png")
        assertThat(feed.episodes).isEmpty()
    }
}
