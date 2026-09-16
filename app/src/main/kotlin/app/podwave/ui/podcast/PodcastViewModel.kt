package app.podwave.ui.podcast

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podwave.data.repository.EpisodeRepository
import app.podwave.data.repository.PodcastRepository
import app.podwave.domain.Episode
import app.podwave.domain.Podcast
import app.podwave.download.Downloads
import app.podwave.ui.EpisodeActions
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PodcastUiState(
    val loading: Boolean = true,
    val podcast: Podcast? = null,
    val episodes: List<Episode> = emptyList(),
    val newestFirst: Boolean = true,
    val hidePlayed: Boolean = false,
    val refreshing: Boolean = false,
)

sealed interface PodcastEvent {
    data class Message(val text: String) : PodcastEvent

    data object Unsubscribed : PodcastEvent
}

class PodcastViewModel(
    savedStateHandle: SavedStateHandle,
    private val podcasts: PodcastRepository,
    episodes: EpisodeRepository,
    private val actions: EpisodeActions,
    private val downloads: Downloads,
) : ViewModel() {

    private val podcastId: Long = checkNotNull(savedStateHandle["podcastId"])
    private val newestFirst = MutableStateFlow(true)
    private val hidePlayed = MutableStateFlow(false)
    private val refreshing = MutableStateFlow(false)
    private val events = Channel<PodcastEvent>(Channel.BUFFERED)
    val eventFlow: Flow<PodcastEvent> = events.receiveAsFlow()

    val uiState: StateFlow<PodcastUiState> = combine(
        podcasts.observePodcast(podcastId),
        episodes.observeForPodcast(podcastId),
        newestFirst,
        hidePlayed,
        refreshing,
    ) { podcast, list, newest, hide, isRefreshing ->
        val visible = list.filter { !hide || !it.completed }
        PodcastUiState(
            loading = false,
            podcast = podcast,
            episodes = if (newest) visible else visible.reversed(),
            newestFirst = newest,
            hidePlayed = hide,
            refreshing = isRefreshing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PodcastUiState())

    fun toggleSort() {
        newestFirst.value = !newestFirst.value
    }

    fun toggleHidePlayed() {
        hidePlayed.value = !hidePlayed.value
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            val result = podcasts.refresh(podcastId)
            refreshing.value = false
            result
                .onSuccess { count -> if (count > 0) events.send(PodcastEvent.Message("$count new episodes")) }
                .onFailure { events.send(PodcastEvent.Message("Couldn't refresh: ${it.message}")) }
        }
    }

    fun unsubscribe() {
        viewModelScope.launch {
            downloads.deleteFiles(podcasts.unsubscribe(podcastId))
            events.send(PodcastEvent.Unsubscribed)
        }
    }

    fun play(episode: Episode) = actions.play(episode)

    fun enqueue(episode: Episode) {
        viewModelScope.launch { events.send(PodcastEvent.Message(actions.enqueue(episode))) }
    }

    fun toggleDownload(episode: Episode) {
        viewModelScope.launch { actions.toggleDownload(episode)?.let { events.send(PodcastEvent.Message(it)) } }
    }
}
