package dev.podwave.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.podwave.data.SettingsRepository
import dev.podwave.data.db.EpisodeDao
import dev.podwave.model.DownloadStatus
import dev.podwave.model.Episode
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface DownloadManager {
    suspend fun download(episode: Episode)

    suspend fun cancel(episodeId: String)

    /** Deletes the downloaded file and returns the episode to streaming. */
    suspend fun delete(episode: Episode)
}

@Singleton
class WorkManagerDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val episodeDao: EpisodeDao,
    private val settings: SettingsRepository,
) : DownloadManager {

    private val workManager get() = WorkManager.getInstance(context)

    override suspend fun download(episode: Episode) {
        if (episode.downloadStatus == DownloadStatus.Downloaded) return

        val wifiOnly = settings.current().downloadOnWifiOnly
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_EPISODE_ID to episode.id,
                    DownloadWorker.KEY_URL to episode.audioUrl,
                    DownloadWorker.KEY_TITLE to episode.title,
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG)
            .build()

        episodeDao.updateDownload(episode.id, DownloadStatus.Queued, 0, null)
        workManager.enqueueUniqueWork(uniqueName(episode.id), ExistingWorkPolicy.KEEP, request)
    }

    override suspend fun cancel(episodeId: String) {
        workManager.cancelUniqueWork(uniqueName(episodeId))
        episodeDao.updateDownload(episodeId, DownloadStatus.None, 0, null)
    }

    override suspend fun delete(episode: Episode) {
        cancel(episode.id)
        episode.localFilePath?.let { File(it).delete() }
        episodeDao.updateDownload(episode.id, DownloadStatus.None, 0, null)
    }

    companion object {
        const val TAG = "episode-download"
        fun uniqueName(episodeId: String) = "download-$episodeId"
    }
}
