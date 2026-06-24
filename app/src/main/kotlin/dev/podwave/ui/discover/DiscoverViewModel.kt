package dev.podwave.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.podwave.core.suspendRunCatching
import dev.podwave.data.PodcastRepository
import dev.podwave.model.DirectoryPodcast
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

sealed interface DirectoryResults {
    data object Loading : DirectoryResults

    /** [term] is empty for the top chart. */
    data class Loaded(val term: String, val podcasts: List<DirectoryPodcast>) : DirectoryResults

    data object Failed : DirectoryResults
}

data class DiscoverUiState(
    val query: String = "",
    val results: DirectoryResults = DirectoryResults.Loading,
    val subscribedFeeds: Set<String> = emptySet(),
)

@OptIn(FlowPreview::class)
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val podcasts: PodcastRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val retries = MutableStateFlow(0)

    /** The chart barely changes within a session; keep it so clearing a search is instant. */
    private var chart: List<DirectoryPodcast>? = null

    private val results: Flow<DirectoryResults> = combine(
        // Search after a pause in typing; going back to the chart needs no pause.
        query.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
            .map { it.trim() }
            .distinctUntilChanged(),
        retries,
    ) { term, _ -> term }
        .flatMapLatest { term -> load(term) }

    val uiState: StateFlow<DiscoverUiState> = combine(
        query,
        results,
        podcasts.observeSubscriptions().map { list -> list.mapTo(mutableSetOf()) { it.feedUrl } },
    ) { text, found, subscribed -> DiscoverUiState(text, found, subscribed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoverUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun clearQuery() {
        query.value = ""
    }

    fun retry() {
        retries.update { it + 1 }
    }

    private fun load(term: String): Flow<DirectoryResults> = flow {
        val cached = chart
        if (term.isEmpty() && cached != null) {
            emit(DirectoryResults.Loaded(term, cached))
            return@flow
        }
        emit(DirectoryResults.Loading)
        val result = suspendRunCatching {
            // The directory occasionally lists a show twice; ids are used as list keys.
            if (term.isEmpty()) {
                podcasts.topPodcasts().distinctBy { it.itunesId }.also { chart = it }
            } else {
                podcasts.search(term).distinctBy { it.itunesId }
            }
        }
        emit(result.fold(onSuccess = { DirectoryResults.Loaded(term, it) }, onFailure = { DirectoryResults.Failed }))
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 400L
    }
}
