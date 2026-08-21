package app.podwave.data.remote

import app.podwave.rss.ParsedFeed
import app.podwave.rss.RssParser
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.StringReader

/** Downloads and parses an RSS feed. Parsing runs off the main thread. */
class FeedFetcher(
    private val http: HttpClient,
    private val parser: RssParser,
    private val parseDispatcher: CoroutineDispatcher,
) {
    suspend fun fetch(feedUrl: String): ParsedFeed {
        val body = http.get(normalizeFeedUrl(feedUrl)).bodyAsText()
        return withContext(parseDispatcher) { parser.parse(StringReader(body)) }
    }
}

/**
 * Accepts what people actually paste: missing scheme, `feed://` and
 * `itpc://` pseudo-schemes, surrounding whitespace.
 */
fun normalizeFeedUrl(input: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.startsWith("feed://", ignoreCase = true) -> "https://" + trimmed.substring(7)
        trimmed.startsWith("itpc://", ignoreCase = true) -> "https://" + trimmed.substring(7)
        trimmed.startsWith("pcast://", ignoreCase = true) -> "https://" + trimmed.substring(8)
        trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        else -> "https://$trimmed"
    }
}
