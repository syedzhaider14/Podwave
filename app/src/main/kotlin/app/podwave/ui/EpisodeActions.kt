package app.podwave.ui

import app.podwave.data.repository.EpisodeRepository
import app.podwave.data.repository.QueueRepository
import app.podwave.domain.DownloadState
import app.podwave.domain.Episode
import app.podwave.download.Downloads
import app.podwave.playback.PlayerController

/** The actions every episode list offers, shared by several ViewModels. */
class EpisodeActions(
    private val player: PlayerController,
    private val queue: QueueRepository,
    private val downloads: Downloads,
    private val episodes: EpisodeRepository,
) {
    fun play(episode: Episode) = player.play(episode)

    /** @return a confirmation message for a snackbar. */
    suspend fun enqueue(episode: Episode): String {
        queue.add(episode.id)
        return "Added to queue"
    }

    suspend fun playNext(episode: Episode): String {
        queue.playNext(episode.id, player.state.value.episodeId)
        return "Playing next"
    }

    /** Starts, retries or cancels a download depending on its current state. */
    suspend fun toggleDownload(episode: Episode): String? = when (episode.downloadState) {
        DownloadState.None, DownloadState.Failed -> {
            downloads.enqueue(episode.id)
            "Download queued"
        }
        DownloadState.Queued, DownloadState.Downloading -> {
            downloads.cancel(episode.id)
            "Download cancelled"
        }
        DownloadState.Downloaded -> null
    }

    suspend fun deleteDownload(episode: Episode) = downloads.delete(episode.id)

    suspend fun setPlayed(episode: Episode, played: Boolean) = episodes.setPlayed(episode.id, played)
}
