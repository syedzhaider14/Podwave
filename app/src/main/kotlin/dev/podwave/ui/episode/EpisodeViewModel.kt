package dev.podwave.ui.episode

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.podwave.data.EpisodeRepository
import dev.podwave.model.Episode
import dev.podwave.ui.common.EpisodeActions
import dev.podwave.ui.common.EpisodeListViewModel
import dev.podwave.ui.common.NowPlaying
import dev.podwave.ui.navigation.EpisodeRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface EpisodeUiState {
    data object Loading : EpisodeUiState

    /** The episode was removed (e.g. its show was deleted) while this screen was open. */
    data object Missing : EpisodeUiState

    data class Loaded(val episode: Episode, val queued: Boolean, val nowPlaying: NowPlaying) : EpisodeUiState
}

@HiltViewModel
class EpisodeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    episodes: EpisodeRepository,
    actions: EpisodeActions,
) : EpisodeListViewModel(actions) {

    private val episodeId = savedStateHandle.toRoute<EpisodeRoute>().episodeId

    val uiState: StateFlow<EpisodeUiState> = combine(
        episodes.observeEpisode(episodeId),
        actions.queuedIds,
        actions.nowPlaying,
    ) { episode, queued, nowPlaying ->
        if (episode == null) {
            EpisodeUiState.Missing
        } else {
            EpisodeUiState.Loaded(episode, queued = episode.id in queued, nowPlaying = nowPlaying)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EpisodeUiState.Loading)
}
