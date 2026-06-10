package dev.podwave.rss

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.StringReader
import java.time.Instant

class RssParserTest {
    private val parser = RssParser { KXmlParser() }

    private fun fixture(name: String) =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("feeds/$name")).reader()

    @Test
    fun `reads channel metadata, preferring iTunes artwork`() {
        val feed = parser.parse(fixture("sample_feed.xml"))

        assertThat(feed.title).isEqualTo("Signal & Noise")
        assertThat(feed.author).isEqualTo("Example Media")
        assertThat(feed.description).isEqualTo("A show about <b>how things work</b>.")
        assertThat(feed.artworkUrl).isEqualTo("https://cdn.example.com/show.jpg")
        assertThat(feed.websiteUrl).isEqualTo("https://signal.example.com")
        assertThat(feed.categories).containsExactly("Technology", "Society & Culture", "Documentary").inOrder()
    }

    @Test
    fun `keeps only playable audio episodes`() {
        val feed = parser.parse(fixture("sample_feed.xml"))
        assertThat(feed.episodes.map { it.title }).containsExactly("Episode 3: Batteries", "Episode 2: Bridges").inOrder()
    }

    @Test
    fun `reads every episode field`() {
        val episode = parser.parse(fixture("sample_feed.xml")).episodes.first()

        assertThat(episode.guid).isEqualTo("signal-003")
        assertThat(episode.description).isEqualTo("<p>Why your phone dies at <i>20%</i>.</p>")
        assertThat(episode.audioUrl).isEqualTo("https://cdn.example.com/003.mp3")
        assertThat(episode.audioMimeType).isEqualTo("audio/mpeg")
        assertThat(episode.audioSizeBytes).isEqualTo(45_120_000L)
        assertThat(episode.durationMs).isEqualTo(3_723_000L)
        assertThat(episode.publishedAt).isEqualTo(Instant.parse("2024-10-02T13:00:00Z"))
        assertThat(episode.season).isEqualTo(1)
        assertThat(episode.episodeNumber).isEqualTo(3)
        assertThat(episode.explicit).isTrue()
        assertThat(episode.artworkUrl).isEqualTo("https://cdn.example.com/003.jpg")
    }

    @Test
    fun `falls back sensibly when optional fields are missing`() {
        val episode = parser.parse(fixture("sample_feed.xml")).episodes[1]

        // No guid: the enclosure URL identifies the episode instead.
        assertThat(episode.guid).isEqualTo("https://cdn.example.com/002.m4a?tracking=1")
        assertThat(episode.description).isEqualTo("Summary from iTunes.")
        assertThat(episode.audioSizeBytes).isNull()
        assertThat(episode.durationMs).isEqualTo(2_723_000L)
        assertThat(episode.publishedAt).isEqualTo(Instant.parse("2024-09-25T13:00:00Z"))
        assertThat(episode.artworkUrl).isEqualTo("https://cdn.example.com/show.jpg")
        assertThat(episode.explicit).isFalse()
    }

    @Test
    fun `rejects documents that are not RSS`() {
        assertThrows(FeedParseException::class.java) {
            parser.parse(StringReader("<html><body>Not a feed</body></html>"))
        }
        assertThrows(FeedParseException::class.java) {
            parser.parse(StringReader("<rss><channel><title>Broken"))
        }
    }
}
