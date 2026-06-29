package dev.podwave

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import dev.podwave.download.FeedRefreshWorker
import io.ktor.client.HttpClient
import javax.inject.Inject

@HiltAndroidApp
class PodwaveApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var httpClient: HttpClient

    // WorkManager's default initializer is removed in the manifest so workers can be injected.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        FeedRefreshWorker.schedule(this)
    }

    /** Artwork goes through the app's HTTP client so it shares the connection pool and user agent. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient = { httpClient })) }
            .crossfade(true)
            .build()
}
