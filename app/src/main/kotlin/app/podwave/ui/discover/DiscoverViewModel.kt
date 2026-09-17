package app.podwave.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podwave.data.remote.DirectoryClient
import app.podwave.data.remote.normalizeFeedUrl
import app.podwave.data.repository.PodcastRepository
import app.podwave.domain.DirectoryPodcast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SearchState {
    data object Idle : SearchState

    data object Loading : SearchState

    data class Results(val podcasts: List<DirectoryPodcast>) : SearchState

    data class Error(val message: String) : SearchState
}

sealed interface DiscoverEvent {
    data class OpenPodcast(val podcastId: Long) : DiscoverEvent

    data class Message(val text: String) : DiscoverEvent
}

class DiscoverViewModel(
    private val directory: DirectoryClient,
    private val podcasts: PodcastRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _search = MutableStateFlow<SearchState>(SearchState.Idle)
    val search: StateFlow<SearchState> = _search.asStateFlow()

    /** Feed URLs already subscribed to, to show "Subscribed" instead of a button. */
    val subscribedFeeds: StateFlow<Set<String>> = podcasts.observeSubscriptions()
        .map { list -> list.mapTo(mutableSetOf()) { it.feedUrl } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _subscribing = MutableStateFlow<Set<String>>(emptySet())
    val subscribing: StateFlow<Set<String>> = _subscribing.asStateFlow()

    private val events = Channel<DiscoverEvent>(Channel.BUFFERED)
    val eventFlow: Flow<DiscoverEvent> = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            _query
                .debounce(SEARCH_DEBOUNCE_MS)
                .map { it.trim() }
                .distinctUntilChanged()
                .filter { it.length >= 2 || it.isEmpty() }
                .collect { term -> runSearch(term) }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun retry() {
        viewModelScope.launch { runSearch(_query.value.trim()) }
    }

    private suspend fun runSearch(term: String) {
        if (term.isEmpty()) {
            _search.value = SearchState.Idle
            return
        }
        _search.value = SearchState.Loading
        _search.value = try {
            SearchState.Results(directory.search(term))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SearchState.Error("Search failed. Check your connection.")
        }
    }

    /** Subscribes to a directory result or a pasted RSS URL. */
    fun subscribe(feedUrl: String) {
        val url = normalizeFeedUrl(feedUrl)
        if (url in _subscribing.value) return
        viewModelScope.launch {
            _subscribing.value += url
            podcasts.subscribe(url)
                .onSuccess { events.send(DiscoverEvent.OpenPodcast(it)) }
                .onFailure { events.send(DiscoverEvent.Message("Couldn't load that feed: ${it.message ?: "unknown error"}")) }
            _subscribing.value -= url
        }
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
