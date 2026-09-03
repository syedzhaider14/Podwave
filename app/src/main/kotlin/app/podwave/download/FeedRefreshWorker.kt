package app.podwave.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.podwave.data.repository.PodcastRepository
import java.util.concurrent.TimeUnit

/** Periodically checks every subscription for new episodes. */
class FeedRefreshWorker(
    context: Context,
    params: WorkerParameters,
    private val podcasts: PodcastRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val summary = podcasts.refreshAll()
        // Partial failures (one dead feed) shouldn't make WorkManager retry everything.
        return if (summary.refreshed == 0 && summary.failed > 0) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "feed-refresh"

        fun schedule(context: Context, enabled: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (!enabled) {
                workManager.cancelUniqueWork(UNIQUE_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<FeedRefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            workManager.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
