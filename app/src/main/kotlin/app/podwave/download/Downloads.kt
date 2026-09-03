package app.podwave.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Environment
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.podwave.R
import app.podwave.data.repository.EpisodeRepository
import app.podwave.data.settings.SettingsRepository
import app.podwave.domain.DownloadState
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.TimeUnit

/** Entry point for starting, cancelling and deleting downloads. */
class Downloads(
    private val context: Context,
    private val episodes: EpisodeRepository,
    private val settings: SettingsRepository,
) {
    private val workManager get() = WorkManager.getInstance(context)

    suspend fun enqueue(episodeId: Long) {
        val wifiOnly = settings.settings.first().downloadOnWifiOnly
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_EPISODE_ID to episodeId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        episodes.updateDownload(episodeId, DownloadState.Queued)
        workManager.enqueueUniqueWork(workName(episodeId), ExistingWorkPolicy.KEEP, request)
    }

    suspend fun cancel(episodeId: Long) {
        workManager.cancelUniqueWork(workName(episodeId))
        episodes.updateDownload(episodeId, DownloadState.None)
    }

    suspend fun delete(episodeId: Long) {
        episodes.get(episodeId)?.localFilePath?.let { File(it).delete() }
        episodes.updateDownload(episodeId, DownloadState.None)
    }

    fun deleteFiles(paths: List<String>) {
        paths.forEach { File(it).delete() }
    }

    companion object {
        const val CHANNEL_ID = "downloads"
        private const val TAG = "episode-download"

        private fun workName(episodeId: Long) = "download-$episodeId"

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.downloads_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        /** App-specific external storage: no permissions needed, removed on uninstall. */
        fun fileFor(context: Context, episodeId: Long, url: String): File {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS) ?: File(context.filesDir, "podcasts")
            dir.mkdirs()
            return File(dir, "$episodeId.${extensionOf(url)}")
        }

        internal fun extensionOf(url: String): String {
            val path = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
            val ext = path.substringAfterLast('.', missingDelimiterValue = "").lowercase()
            return ext.takeIf { it.length in 2..4 && it.all(Char::isLetterOrDigit) } ?: "mp3"
        }
    }
}
