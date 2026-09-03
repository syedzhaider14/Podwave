package app.podwave.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.podwave.R
import app.podwave.data.repository.EpisodeRepository
import app.podwave.domain.DownloadState
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * Downloads one episode to app-specific storage, reporting progress to
 * WorkManager and the database. Writes to a temporary file and renames on
 * success, so a half-finished file is never mistaken for a download.
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val episodes: EpisodeRepository,
    private val http: HttpClient,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val episodeId = inputData.getLong(KEY_EPISODE_ID, -1)
        val episode = episodes.get(episodeId) ?: return Result.failure()

        val target = Downloads.fileFor(applicationContext, episode.id, episode.audioUrl)
        val partial = File(target.parentFile, target.name + ".part")
        setForeground(foregroundInfo(episode.title, progress = 0))
        episodes.updateDownload(episodeId, DownloadState.Downloading, 0)

        return try {
            http.prepareGet(episode.audioUrl).execute { response ->
                val total = response.contentLength() ?: -1L
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(BUFFER_SIZE)
                var written = 0L
                var lastReported = -1

                partial.outputStream().buffered().use { output ->
                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read <= 0) continue
                        output.write(buffer, 0, read)
                        written += read
                        val percent = if (total > 0) ((written * 100) / total).toInt() else -1
                        if (percent >= 0 && percent - lastReported >= REPORT_STEP) {
                            lastReported = percent
                            setProgress(workDataOf(KEY_PROGRESS to percent))
                            setForeground(foregroundInfo(episode.title, percent))
                            episodes.updateDownload(episodeId, DownloadState.Downloading, percent)
                        }
                    }
                }
            }
            if (!partial.renameTo(target)) error("Couldn't move download into place")
            episodes.updateDownload(episodeId, DownloadState.Downloaded, 100, target.absolutePath)
            Result.success()
        } catch (e: CancellationException) {
            partial.delete()
            episodes.updateDownload(episodeId, DownloadState.None)
            throw e
        } catch (e: Exception) {
            partial.delete()
            if (runAttemptCount < MAX_ATTEMPTS) {
                episodes.updateDownload(episodeId, DownloadState.Queued)
                Result.retry()
            } else {
                episodes.updateDownload(episodeId, DownloadState.Failed)
                Result.failure(workDataOf(KEY_ERROR to (e.message ?: e.javaClass.simpleName)))
            }
        }
    }

    private fun foregroundInfo(title: String, progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, Downloads.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.downloading))
            .setContentText(title)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress.coerceAtLeast(0), progress < 0)
            .build()
        val notificationId = NOTIFICATION_BASE + inputData.getLong(KEY_EPISODE_ID, 0).toInt()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    companion object {
        const val KEY_EPISODE_ID = "episode_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        private const val BUFFER_SIZE = 64 * 1024
        private const val REPORT_STEP = 2
        private const val MAX_ATTEMPTS = 3
        private const val NOTIFICATION_BASE = 20_000
    }
}
