package dev.podwave.ui.library

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.podwave.data.EpisodeRepository
import dev.podwave.data.PodcastRepository
import dev.podwave.model.Episode
import dev.podwave.model.Podcast
import dev.podwave.ui.common.EpisodeActions
import dev.podwave.ui.common.EpisodeListViewModel
import dev.podwave.ui.common.NowPlaying
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class LibraryUiState(
    val loading: Boolean = true,
    val subscriptions: List<Podcast> = emptyList(),
    val downloads: List<Episode> = emptyList(),
    /** From the feeds' enclosure sizes, so approximate; good enough to spot a full phone. */
    val downloadsSizeBytes: Long = 0,
    val nowPlaying: NowPlaying = NowPlaying(),
    val queuedIds: Set<String> = emptySet(),
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    podcasts: PodcastRepository,
    episodes: EpisodeRepository,
    actions: EpisodeActions,
) : EpisodeListViewModel(actions) {

    val uiState: StateFlow<LibraryUiState> = combine(
        podcasts.observeSubscriptions(),
        episodes.observeDownloaded(),
        actions.nowPlaying,
        actions.queuedIds,
    ) { subscriptions, downloads, nowPlaying, queued ->
        LibraryUiState(
            loading = false,
            subscriptions = subscriptions,
            downloads = downloads,
            downloadsSizeBytes = downloads.sumOf { it.audioSizeBytes ?: 0L },
            nowPlaying = nowPlaying,
            queuedIds = queued,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())
}
