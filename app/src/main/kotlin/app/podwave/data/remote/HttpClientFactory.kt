package app.podwave.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object HttpClientFactory {
    const val USER_AGENT = "Podwave/1.0 (Android podcast player)"

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    /**
     * Shared client for feeds, directory search and downloads. iTunes returns
     * JSON as `text/javascript`, so that content type is registered too.
     */
    fun create(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
        expectSuccess = true
        install(UserAgent) { agent = USER_AGENT }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
        install(ContentNegotiation) {
            json(json)
            json(json, contentType = io.ktor.http.ContentType("text", "javascript"))
        }
    }
}
