package dev.podwave.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.podwave.data.EpisodeRepository
import dev.podwave.data.PodcastRepository
import dev.podwave.data.SettingsRepository
import java.util.concurrent.TimeUnit

/**
 * Checks every subscription for new episodes in the background and, if the user opted in,
 * downloads them so they are ready offline.
 */
@HiltWorker
class FeedRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val podcasts: PodcastRepository,
    private val episodes: EpisodeRepository,
    private val settings: SettingsRepository,
    private val downloads: DownloadManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val results = podcasts.refreshSubscriptions()
        if (settings.current().autoDownloadNewEpisodes) {
            val newIds = results.values.flatMap { it.getOrNull()?.newEpisodeIds.orEmpty() }
            episodes.getEpisodes(newIds).forEach { downloads.download(it) }
        }
        // Partial failure (one dead feed) should not make WorkManager retry every feed.
        val everythingFailed = results.isNotEmpty() && results.values.all { it.isFailure }
        return if (everythingFailed) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "feed-refresh"
        private const val UNIQUE_NAME_NOW = "feed-refresh-now"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FeedRefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** One-off refresh from the settings screen; a second tap while one is running is ignored. */
        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<FeedRefreshWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME_NOW, ExistingWorkPolicy.KEEP, request)
        }
    }
}
