package dev.podwave.ui.home

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.podwave.data.EpisodeRepository
import dev.podwave.data.PodcastRepository
import dev.podwave.model.Episode
import dev.podwave.ui.common.EpisodeActions
import dev.podwave.ui.common.EpisodeListViewModel
import dev.podwave.ui.common.NowPlaying
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val hasSubscriptions: Boolean = false,
    val continueListening: List<Episode> = emptyList(),
    val queue: List<Episode> = emptyList(),
    val latest: List<Episode> = emptyList(),
    val nowPlaying: NowPlaying = NowPlaying(),
    val refreshing: Boolean = false,
) {
    val isEmpty: Boolean get() = continueListening.isEmpty() && queue.isEmpty() && latest.isEmpty()
}

private data class HomeContent(
    val hasSubscriptions: Boolean,
    val continueListening: List<Episode>,
    val queue: List<Episode>,
    val latest: List<Episode>,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val podcasts: PodcastRepository,
    private val episodes: EpisodeRepository,
    actions: EpisodeActions,
) : EpisodeListViewModel(actions) {

    private val refreshing = MutableStateFlow(false)

    private val content = combine(
        podcasts.observeSubscriptions().map { it.isNotEmpty() },
        episodes.observeInProgress(),
        episodes.observeQueue(),
        episodes.observeLatest(),
    ) { hasSubscriptions, inProgress, queue, latest ->
        val queued = queue.mapTo(mutableSetOf()) { it.id }
        HomeContent(
            hasSubscriptions = hasSubscriptions,
            continueListening = inProgress,
            queue = queue,
            // Queued episodes already have a place on this screen.
            latest = latest.filterNot { it.id in queued },
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(content, actions.nowPlaying, refreshing) { home, nowPlaying, isRefreshing ->
        HomeUiState(
            loading = false,
            hasSubscriptions = home.hasSubscriptions,
            continueListening = home.continueListening,
            queue = home.queue,
            latest = home.latest,
            nowPlaying = nowPlaying,
            refreshing = isRefreshing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            try {
                podcasts.refreshSubscriptions()
            } finally {
                refreshing.value = false
            }
        }
    }

    fun removeFromQueue(episode: Episode) {
        viewModelScope.launch { episodes.removeFromQueue(episode.id) }
    }

    fun clearQueue() {
        viewModelScope.launch { episodes.clearQueue() }
    }
}
