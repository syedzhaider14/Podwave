package dev.podwave.network

import dev.podwave.core.IoDispatcher
import dev.podwave.rss.ParsedFeed
import dev.podwave.rss.RssParser
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface FeedResult {
    data class Updated(val feed: ParsedFeed, val etag: String?) : FeedResult

    /** The server confirmed nothing changed since the last refresh (HTTP 304). */
    data object NotModified : FeedResult
}

class FeedHttpException(val status: Int) : Exception("Feed request failed with HTTP $status")

/**
 * Downloads and parses a feed in a streaming fashion, so a 5 MB feed with a thousand episodes
 * never has to be held in memory as a single string. Sends `If-None-Match` so unchanged feeds
 * cost almost nothing to refresh.
 */
class FeedFetcher @Inject constructor(
    private val client: HttpClient,
    private val parser: RssParser,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun fetch(feedUrl: String, etag: String? = null): FeedResult = withContext(io) {
        // prepareGet + execute keeps the body as a live stream instead of buffering it.
        client.prepareGet(feedUrl) {
            if (etag != null) header(HttpHeaders.IfNoneMatch, etag)
        }.execute { response ->
            when {
                response.status == HttpStatusCode.NotModified -> FeedResult.NotModified
                !response.status.isSuccess() -> throw FeedHttpException(response.status.value)
                else -> {
                    val feed = response.bodyAsChannel().toInputStream().use { parser.parse(it) }
                    FeedResult.Updated(feed, response.headers[HttpHeaders.ETag])
                }
            }
        }
    }
}
