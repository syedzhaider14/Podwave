package app.podwave.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podwave.data.repository.EpisodeRepository
import app.podwave.data.repository.PodcastRepository
import app.podwave.domain.Episode
import app.podwave.domain.Podcast
import app.podwave.ui.EpisodeActions
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryFilter(val label: String) {
    Latest("Latest"),
    InProgress("In progress"),
    Downloaded("Downloaded"),
}

data class LibraryUiState(
    val loaded: Boolean = false,
    val subscriptions: List<Podcast> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val filter: LibraryFilter = LibraryFilter.Latest,
    val isRefreshing: Boolean = false,
)

class LibraryViewModel(
    private val podcasts: PodcastRepository,
    private val episodes: EpisodeRepository,
    private val actions: EpisodeActions,
) : ViewModel() {

    private val filter = MutableStateFlow(LibraryFilter.Latest)
    private val refreshing = MutableStateFlow(false)
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    private val filteredEpisodes = filter.flatMapLatest { selected ->
        when (selected) {
            LibraryFilter.Latest -> episodes.observeLatest()
            LibraryFilter.InProgress -> episodes.observeInProgress()
            LibraryFilter.Downloaded -> episodes.observeDownloads()
        }
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        podcasts.observeSubscriptions(),
        filteredEpisodes,
        filter,
        refreshing,
    ) { subscriptions, list, selected, isRefreshing ->
        LibraryUiState(
            loaded = true,
            subscriptions = subscriptions,
            episodes = list,
            filter = selected,
            isRefreshing = isRefreshing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setFilter(value: LibraryFilter) {
        filter.value = value
    }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            val summary = try {
                podcasts.refreshAll()
            } finally {
                refreshing.value = false
            }
            val message = when {
                summary.newEpisodes > 0 -> "${summary.newEpisodes} new episodes"
                summary.failed > 0 -> "Couldn't refresh ${summary.failed} podcasts"
                else -> "You're up to date"
            }
            messageChannel.send(message)
        }
    }

    fun play(episode: Episode) = actions.play(episode)

    fun enqueue(episode: Episode) {
        viewModelScope.launch { messageChannel.send(actions.enqueue(episode)) }
    }

    fun toggleDownload(episode: Episode) {
        viewModelScope.launch { actions.toggleDownload(episode)?.let { messageChannel.send(it) } }
    }
}
