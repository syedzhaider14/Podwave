package app.podwave.ui.episode

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podwave.data.repository.EpisodeRepository
import app.podwave.domain.Episode
import app.podwave.ui.EpisodeActions
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface EpisodeUiState {
    data object Loading : EpisodeUiState

    data object Missing : EpisodeUiState

    data class Ready(val episode: Episode) : EpisodeUiState
}

class EpisodeViewModel(
    savedStateHandle: SavedStateHandle,
    episodes: EpisodeRepository,
    private val actions: EpisodeActions,
) : ViewModel() {
    private val episodeId: Long = checkNotNull(savedStateHandle["episodeId"])
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    val uiState: StateFlow<EpisodeUiState> = episodes.observe(episodeId)
        .map { it?.let(EpisodeUiState::Ready) ?: EpisodeUiState.Missing }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EpisodeUiState.Loading)

    private fun current(): Episode? = (uiState.value as? EpisodeUiState.Ready)?.episode

    fun play() {
        current()?.let(actions::play)
    }

    fun enqueue() = launchWith { messageChannel.send(actions.enqueue(it)) }

    fun playNext() = launchWith { messageChannel.send(actions.playNext(it)) }

    fun toggleDownload() = launchWith { episode -> actions.toggleDownload(episode)?.let { messageChannel.send(it) } }

    fun deleteDownload() = launchWith { actions.deleteDownload(it) }

    fun togglePlayed() = launchWith { actions.setPlayed(it, played = !it.completed) }

    private fun launchWith(block: suspend (Episode) -> Unit) {
        val episode = current() ?: return
        viewModelScope.launch { block(episode) }
    }
}
