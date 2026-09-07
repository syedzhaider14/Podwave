package app.podwave

import app.podwave.data.remote.DirectoryClient
import app.podwave.data.remote.FeedFetcher
import app.podwave.data.remote.HttpClientFactory
import app.podwave.data.remote.normalizeFeedUrl
import app.podwave.download.Downloads
import app.podwave.rss.FeedParseException
import app.podwave.rss.RssParser
import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.kxml2.io.KXmlParser

class NetworkTest {

    @Test
    fun `directory search maps results and drops entries without feeds`() = runTest {
        var params: Parameters = Parameters.Empty
        val engine = MockEngine { request ->
            params = request.url.parameters
            respond(
                content = """
                    {"resultCount": 3, "results": [
                      {"collectionName": "Hard Fork", "artistName": "The New York Times",
                       "feedUrl": "https://feeds.example.com/hardfork", "artworkUrl600": "https://img/600.jpg",
                       "primaryGenreName": "Technology", "trackCount": 120},
                      {"collectionName": "No Feed", "artistName": "Somebody"},
                      {"collectionName": "Hard Fork (dup)", "feedUrl": "https://feeds.example.com/hardfork"}
                    ]}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                // iTunes really does label its JSON as JavaScript.
                headers = headersOf(HttpHeaders.ContentType, "text/javascript; charset=utf-8"),
            )
        }
        val client = DirectoryClient(HttpClientFactory.create(engine))

        val results = client.search("  hard fork ")

        assertThat(results).hasSize(1)
        assertThat(results.single().title).isEqualTo("Hard Fork")
        assertThat(results.single().artworkUrl).isEqualTo("https://img/600.jpg")
        assertThat(results.single().episodeCount).isEqualTo(120)
        assertThat(params["term"]).isEqualTo("hard fork")
        assertThat(params["media"]).isEqualTo("podcast")
    }

    @Test
    fun `blank searches don't hit the network`() = runTest {
        val engine = MockEngine { error("should not be called") }
        assertThat(DirectoryClient(HttpClientFactory.create(engine)).search("   ")).isEmpty()
    }

    @Test
    fun `feed fetcher parses the downloaded feed`() = runTest {
        val engine = MockEngine {
            respond(
                content = """<rss version="2.0"><channel><title>Tiny</title>
                    <item><title>One</title><enclosure url="https://x.example/1.mp3" type="audio/mpeg"/></item>
                    </channel></rss>""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/rss+xml"),
            )
        }
        val fetcher = FeedFetcher(
            http = HttpClientFactory.create(engine),
            parser = RssParser { KXmlParser() },
            parseDispatcher = StandardTestDispatcher(testScheduler),
        )

        val feed = fetcher.fetch("feeds.example.com/tiny")

        assertThat(feed.title).isEqualTo("Tiny")
        assertThat(feed.episodes.single().audioUrl).isEqualTo("https://x.example/1.mp3")
        assertThat(engine.requestHistory.single().url.toString()).isEqualTo("https://feeds.example.com/tiny")
    }

    @Test
    fun `http errors and non-feeds surface as exceptions`() = runTest {
        val notFound = FeedFetcher(
            HttpClientFactory.create(MockEngine { respond("nope", HttpStatusCode.NotFound) }),
            RssParser { KXmlParser() },
            StandardTestDispatcher(testScheduler),
        )
        val html = FeedFetcher(
            HttpClientFactory.create(MockEngine { respond("<html><body>hi</body></html>", HttpStatusCode.OK) }),
            RssParser { KXmlParser() },
            StandardTestDispatcher(testScheduler),
        )
        assertThat(runCatching { notFound.fetch("https://x.example/feed") }.exceptionOrNull())
            .isInstanceOf(ClientRequestException::class.java)
        assertThat(runCatching { html.fetch("https://x.example/feed") }.exceptionOrNull())
            .isInstanceOf(FeedParseException::class.java)
    }

    @Test
    fun `pasted feed urls are normalised`() {
        assertThat(normalizeFeedUrl(" feed://example.com/rss ")).isEqualTo("https://example.com/rss")
        assertThat(normalizeFeedUrl("itpc://example.com/rss")).isEqualTo("https://example.com/rss")
        assertThat(normalizeFeedUrl("example.com/rss")).isEqualTo("https://example.com/rss")
        assertThat(normalizeFeedUrl("http://example.com/rss")).isEqualTo("http://example.com/rss")
    }

    @Test
    fun `download file extensions come from the url`() {
        assertThat(Downloads.extensionOf("https://cdn.example.com/ep/42.m4a?token=abc")).isEqualTo("m4a")
        assertThat(Downloads.extensionOf("https://cdn.example.com/ep/42.MP3")).isEqualTo("mp3")
        assertThat(Downloads.extensionOf("https://cdn.example.com/stream")).isEqualTo("mp3")
        assertThat(Downloads.extensionOf("https://cdn.example.com/x.weird-ext")).isEqualTo("mp3")
    }
}
