package dev.podwave.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.podwave.data.EpisodeRepository
import dev.podwave.download.DownloadManager
import dev.podwave.model.DownloadStatus
import dev.podwave.model.Episode
import dev.podwave.playback.PlayerController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What is loaded in the player, so lists can highlight it and swap play for pause. */
data class NowPlaying(val episodeId: String? = null, val isPlaying: Boolean = false) {
    fun isCurrent(episode: Episode) = episode.id == episodeId

    fun isPlayingEpisode(episode: Episode) = isPlaying && isCurrent(episode)
}

/** The things every episode list lets you do, shared so each screen behaves the same way. */
class EpisodeActions @Inject constructor(
    private val player: PlayerController,
    private val episodes: EpisodeRepository,
    private val downloads: DownloadManager,
) {
    val nowPlaying: Flow<NowPlaying> = player.state
        .map { NowPlaying(it.episodeId, it.isPlaying) }
        .distinctUntilChanged()

    val queuedIds: Flow<Set<String>> = episodes.observeQueue()
        .map { queue -> queue.mapTo(mutableSetOf()) { it.id } }
        .distinctUntilChanged()

    suspend fun playOrPause(episode: Episode) {
        val state = player.state.value
        if (state.episodeId == episode.id && state.isPlaying) player.togglePlayPause() else player.play(episode)
    }

    /** Starts (or jumps within) an episode at a timestamp, e.g. one tapped in the show notes. */
    suspend fun playFrom(episode: Episode, positionMs: Long) {
        player.play(episode)
        player.seekTo(positionMs)
    }

    suspend fun toggleQueued(episode: Episode, queued: Boolean) {
        if (queued) episodes.removeFromQueue(episode.id) else episodes.addToQueue(episode.id, playNext = false)
    }

    suspend fun playNext(episode: Episode) = episodes.addToQueue(episode.id, playNext = true)

    suspend fun togglePlayed(episode: Episode) {
        if (episode.isPlayed) episodes.markUnplayed(episode.id) else episodes.markPlayed(episode.id)
    }

    /** One button cycles through the download lifecycle: start, cancel, or remove the file. */
    suspend fun onDownloadClicked(episode: Episode) = when (episode.downloadStatus) {
        DownloadStatus.None, DownloadStatus.Failed -> downloads.download(episode)
        DownloadStatus.Queued, DownloadStatus.Downloading -> downloads.cancel(episode.id)
        DownloadStatus.Downloaded -> downloads.delete(episode)
    }
}

/** Base for screens that show episode rows; wires the row callbacks to [EpisodeActions]. */
abstract class EpisodeListViewModel(protected val actions: EpisodeActions) : ViewModel() {
    fun playOrPause(episode: Episode) {
        viewModelScope.launch { actions.playOrPause(episode) }
    }

    fun playFrom(episode: Episode, positionMs: Long) {
        viewModelScope.launch { actions.playFrom(episode, positionMs) }
    }

    fun toggleQueued(episode: Episode, queued: Boolean) {
        viewModelScope.launch { actions.toggleQueued(episode, queued) }
    }

    fun playNext(episode: Episode) {
        viewModelScope.launch { actions.playNext(episode) }
    }

    fun togglePlayed(episode: Episode) {
        viewModelScope.launch { actions.togglePlayed(episode) }
    }

    fun onDownloadClicked(episode: Episode) {
        viewModelScope.launch { actions.onDownloadClicked(episode) }
    }
}
