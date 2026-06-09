package dev.podwave.network

import com.google.common.truth.Truth.assertThat
import dev.podwave.rss.RssParser
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Test
import org.kxml2.io.KXmlParser

class FeedFetcherTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val feedXml = requireNotNull(javaClass.classLoader?.getResource("feeds/sample_feed.xml")).readText()

    private fun TestScope.fetcher(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = FeedFetcher(
        client = HttpClient(
            MockEngine { request ->
                requests += request
                handler(request)
            },
        ),
        parser = RssParser { KXmlParser() },
        io = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun `parses the feed and remembers its ETag`() = runTest {
        val fetcher = fetcher {
            respond(feedXml, headers = headersOf(HttpHeaders.ETag, "\"v2\""))
        }

        val result = fetcher.fetch("https://feeds.example.com/signal")

        assertThat(result).isInstanceOf(FeedResult.Updated::class.java)
        result as FeedResult.Updated
        assertThat(result.feed.title).isEqualTo("Signal & Noise")
        assertThat(result.feed.episodes).isNotEmpty()
        assertThat(result.etag).isEqualTo("\"v2\"")
        assertThat(requests.single().headers[HttpHeaders.IfNoneMatch]).isNull()
    }

    @Test
    fun `asks for changes only and accepts 304`() = runTest {
        val fetcher = fetcher { respond("", HttpStatusCode.NotModified) }

        val result = fetcher.fetch("https://feeds.example.com/signal", etag = "\"v2\"")

        assertThat(result).isEqualTo(FeedResult.NotModified)
        assertThat(requests.single().headers[HttpHeaders.IfNoneMatch]).isEqualTo("\"v2\"")
    }

    @Test
    fun `reports HTTP errors with their status`() = runTest {
        val fetcher = fetcher { respond("Gone", HttpStatusCode.Gone) }

        try {
            fetcher.fetch("https://feeds.example.com/signal")
            fail("Expected FeedHttpException")
        } catch (error: FeedHttpException) {
            assertThat(error.status).isEqualTo(410)
        }
    }
}
