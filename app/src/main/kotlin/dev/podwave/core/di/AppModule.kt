package dev.podwave.core.di

import android.content.Context
import android.util.Xml
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.podwave.BuildConfig
import dev.podwave.core.ApplicationScope
import dev.podwave.core.IoDispatcher
import dev.podwave.data.db.EpisodeDao
import dev.podwave.data.db.PodcastDao
import dev.podwave.data.db.PodwaveDatabase
import dev.podwave.data.db.QueueDao
import dev.podwave.rss.RssParser
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @IoDispatcher
    fun providesIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @ApplicationScope
    fun providesApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    fun providesClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun providesJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun providesHttpClient(json: Json): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) {
            // Apple serves JSON as text/javascript, so register for both.
            json(json)
            json(json, contentType = io.ktor.http.ContentType.Text.JavaScript)
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 60_000
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 2)
            retryOnException(maxRetries = 2, retryOnTimeout = true)
            exponentialDelay()
        }
        if (BuildConfig.DEBUG) {
            install(Logging) { level = LogLevel.INFO }
        }
        defaultRequest {
            headers.append(HttpHeaders.UserAgent, "Podwave/${BuildConfig.VERSION_NAME} (Android)")
        }
    }

    @Provides
    fun providesRssParser(): RssParser = RssParser { Xml.newPullParser() }

    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): PodwaveDatabase =
        Room.databaseBuilder(context, PodwaveDatabase::class.java, "podwave.db").build()

    @Provides
    fun providesPodcastDao(db: PodwaveDatabase): PodcastDao = db.podcastDao()

    @Provides
    fun providesEpisodeDao(db: PodwaveDatabase): EpisodeDao = db.episodeDao()

    @Provides
    fun providesQueueDao(db: PodwaveDatabase): QueueDao = db.queueDao()

    @Provides
    @Singleton
    fun providesPreferencesDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(scope.coroutineContext + Dispatchers.IO),
        produceFile = { context.preferencesDataStoreFile("settings") },
    )
}
