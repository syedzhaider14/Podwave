package dev.podwave.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.podwave.R
import dev.podwave.data.db.EpisodeDao
import dev.podwave.model.DownloadStatus
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Streams an episode to app storage. Writes go to a `.part` file that is renamed only when
 * complete, so a cancelled or failed download never leaves a truncated file that looks valid.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val client: HttpClient,
    private val episodeDao: EpisodeDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val episodeId = inputData.getString(KEY_EPISODE_ID) ?: return Result.failure()
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE).orEmpty()

        val directory = File(applicationContext.filesDir, "episodes").apply { mkdirs() }
        val target = File(directory, DownloadFiles.fileName(episodeId, url))
        val partial = File(directory, "${target.name}.part")

        runCatching { setForeground(foregroundInfo(title, progress = 0)) }

        return try {
            episodeDao.updateDownload(episodeId, DownloadStatus.Downloading, 0, null)
            client.prepareGet(url).execute { response ->
                if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
                val total = response.contentLength()
                var copied = 0L
                var lastReported = -1

                response.bodyAsChannel().toInputStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            copied += read
                            val percent = DownloadFiles.progressPercent(copied, total)
                            if (percent >= lastReported + PROGRESS_STEP) {
                                lastReported = percent
                                setProgress(workDataOf(KEY_PROGRESS to percent))
                                episodeDao.updateDownload(episodeId, DownloadStatus.Downloading, percent, null)
                                runCatching { setForeground(foregroundInfo(title, percent)) }
                            }
                        }
                    }
                }
            }
            if (!partial.renameTo(target)) error("Could not move the finished download into place")
            episodeDao.updateDownload(episodeId, DownloadStatus.Downloaded, 100, target.absolutePath)
            Result.success()
        } catch (cancellation: CancellationException) {
            partial.delete()
            episodeDao.updateDownload(episodeId, DownloadStatus.None, 0, null)
            throw cancellation
        } catch (error: Exception) {
            partial.delete()
            if (runAttemptCount < MAX_ATTEMPTS) {
                episodeDao.updateDownload(episodeId, DownloadStatus.Queued, 0, null)
                Result.retry()
            } else {
                episodeDao.updateDownload(episodeId, DownloadStatus.Failed, 0, null)
                Result.failure()
            }
        }
    }

    /** Expedited work on Android 11 and lower runs as a foreground service and asks for this up front. */
    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(inputData.getString(KEY_TITLE).orEmpty(), progress = 0)

    private fun foregroundInfo(title: String, progress: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.notification_channel_downloads),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.notification_downloading))
            .setContentText(title)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        // One notification per download, derived from this work request's id.
        val notificationId = NOTIFICATION_BASE_ID + (this.id.hashCode() and 0xffff)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    companion object {
        const val KEY_EPISODE_ID = "episode_id"
        const val KEY_URL = "url"
        const val KEY_TITLE = "title"
        const val KEY_PROGRESS = "progress"
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_BASE_ID = 20_000
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_STEP = 2
        private const val MAX_ATTEMPTS = 3
    }
}
