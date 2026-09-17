package app.podwave.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podwave.data.repository.QueueRepository
import app.podwave.domain.Episode
import app.podwave.playback.PlayerController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class QueueUiState(
    val loaded: Boolean = false,
    val episodes: List<Episode> = emptyList(),
    val nowPlayingId: Long? = null,
) {
    val totalRemainingSeconds: Long
        get() = episodes.sumOf { (it.durationSeconds ?: 0) - it.playbackPositionMs / 1000 }.coerceAtLeast(0)
}

class QueueViewModel(
    private val queue: QueueRepository,
    private val player: PlayerController,
) : ViewModel() {

    val uiState: StateFlow<QueueUiState> = combine(queue.observe(), player.state) { episodes, playerState ->
        QueueUiState(loaded = true, episodes = episodes, nowPlayingId = playerState.episodeId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QueueUiState())

    fun play(episode: Episode) = player.play(episode)

    fun moveUp(index: Int) = move(index, index - 1)

    fun moveDown(index: Int) = move(index, index + 1)

    fun move(from: Int, to: Int) {
        viewModelScope.launch { queue.move(from, to) }
    }

    fun remove(episode: Episode) {
        viewModelScope.launch { queue.remove(episode.id) }
    }

    fun clear() {
        viewModelScope.launch { queue.clear() }
    }
}
