package dev.podwave.network

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class ITunesApiTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun api(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): ITunesApi {
        val json = Json { ignoreUnknownKeys = true }
        val client = HttpClient(
            MockEngine { request ->
                requests += request
                handler(request)
            },
        ) {
            install(ContentNegotiation) {
                json(json)
                json(json, contentType = ContentType.Text.JavaScript)
            }
        }
        return ITunesApi(client)
    }

    // Apple really does label these responses text/javascript.
    private fun MockRequestHandleScope.javascript(body: String) =
        respond(body, headers = headersOf(HttpHeaders.ContentType, "text/javascript; charset=utf-8"))

    @Test
    fun `search maps results and drops unusable entries`() = runTest {
        val api = api {
            javascript(
                """
                {"resultCount":3,"results":[
                  {"wrapperType":"track","kind":"podcast","collectionId":101,"collectionName":"Signal & Noise",
                   "artistName":"Example Media","feedUrl":"https://feeds.example.com/signal",
                   "artworkUrl100":"https://img.example.com/abc/100x100bb.jpg",
                   "artworkUrl600":"https://img.example.com/abc/600x600bb.jpg","primaryGenreName":"Technology"},
                  {"collectionId":102,"collectionName":"No Feed","artistName":"Someone",
                   "artworkUrl100":"https://img.example.com/def/100x100bb.jpg"},
                  {"collectionId":103,"collectionName":"","artistName":"Nameless"}
                ]}
                """.trimIndent(),
            )
        }

        val results = api.search("signal")

        assertThat(results.map { it.itunesId }).containsExactly(101L, 102L).inOrder()
        with(results[0]) {
            assertThat(title).isEqualTo("Signal & Noise")
            assertThat(author).isEqualTo("Example Media")
            assertThat(feedUrl).isEqualTo("https://feeds.example.com/signal")
            assertThat(artworkUrl).isEqualTo("https://img.example.com/abc/600x600bb.jpg")
            assertThat(genre).isEqualTo("Technology")
        }
        assertThat(results[1].feedUrl).isNull()
        assertThat(results[1].artworkUrl).isEqualTo("https://img.example.com/def/600x600bb.jpg")

        val url = requests.single().url
        assertThat(url.host).isEqualTo("itunes.apple.com")
        assertThat(url.parameters["term"]).isEqualTo("signal")
        assertThat(url.parameters["media"]).isEqualTo("podcast")
    }

    @Test
    fun `top chart parses ids and hides the generic genre`() = runTest {
        val api = api {
            respond(
                """
                {"feed":{"title":"Top Shows","results":[
                  {"artistName":"Example Media","id":"101","name":"Signal & Noise","kind":"podcasts",
                   "artworkUrl100":"https://img.example.com/abc/100x100bb.png",
                   "genres":[{"genreId":"1318","name":"Technology"}]},
                  {"artistName":"Broken","id":"not-a-number","name":"Broken"},
                  {"artistName":"Third","id":"103","name":"Third Show","genres":[{"name":"Podcasts"}]}
                ]}}
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val chart = api.topPodcasts()

        assertThat(chart.map { it.itunesId }).containsExactly(101L, 103L).inOrder()
        assertThat(chart[0].artworkUrl).isEqualTo("https://img.example.com/abc/600x600bb.png")
        assertThat(chart[0].genre).isEqualTo("Technology")
        assertThat(chart[0].feedUrl).isNull()
        assertThat(chart[1].genre).isNull()
    }

    @Test
    fun `lookup returns the feed for a chart entry`() = runTest {
        val api = api {
            javascript(
                """
                {"resultCount":1,"results":[
                  {"collectionId":101,"collectionName":"Signal & Noise","feedUrl":"https://feeds.example.com/signal"}
                ]}
                """.trimIndent(),
            )
        }

        assertThat(api.lookup(101)?.feedUrl).isEqualTo("https://feeds.example.com/signal")
        assertThat(requests.single().url.parameters["id"]).isEqualTo("101")
    }

    @Test
    fun `lookup of an unknown id returns null`() = runTest {
        val api = api { javascript("""{"resultCount":0,"results":[]}""") }
        assertThat(api.lookup(999)).isNull()
    }

    @Test
    fun `artwork URLs are rewritten to a large size`() {
        assertThat(upscaleArtwork("https://img.example.com/x/100x100bb.jpg")).isEqualTo("https://img.example.com/x/600x600bb.jpg")
        assertThat(upscaleArtwork("https://img.example.com/x/200x200.png")).isEqualTo("https://img.example.com/x/600x600bb.png")
        assertThat(upscaleArtwork("https://img.example.com/x/cover.jpg")).isEqualTo("https://img.example.com/x/cover.jpg")
    }
}
