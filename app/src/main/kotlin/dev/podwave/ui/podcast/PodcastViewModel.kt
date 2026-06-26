package dev.podwave.ui.podcast

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.podwave.core.suspendRunCatching
import dev.podwave.data.EpisodeRepository
import dev.podwave.data.PodcastRepository
import dev.podwave.model.DirectoryPodcast
import dev.podwave.model.DownloadStatus
import dev.podwave.model.Episode
import dev.podwave.model.Podcast
import dev.podwave.ui.common.EpisodeActions
import dev.podwave.ui.common.EpisodeListViewModel
import dev.podwave.ui.common.NowPlaying
import dev.podwave.ui.navigation.PodcastRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

enum class EpisodeFilter {
    All,
    Unplayed,
    Downloaded,
    ;

    fun apply(episodes: List<Episode>): List<Episode> = when (this) {
        All -> episodes
        Unplayed -> episodes.filterNot { it.isPlayed }
        Downloaded -> episodes.filter { it.downloadStatus == DownloadStatus.Downloaded }
    }
}

enum class PodcastError {
    /** A chart entry whose feed URL the directory couldn't provide. */
    FeedNotFound,
    LoadFailed,
}

data class PodcastUiState(
    val title: String = "",
    val artworkUrl: String? = null,
    val podcast: Podcast? = null,
    val episodes: List<Episode> = emptyList(),
    val totalEpisodes: Int = 0,
    val filter: EpisodeFilter = EpisodeFilter.All,
    val refreshing: Boolean = false,
    val error: PodcastError? = null,
    val nowPlaying: NowPlaying = NowPlaying(),
    val queuedIds: Set<String> = emptySet(),
) {
    val loading: Boolean get() = podcast == null && error == null
}

private data class LoadStatus(val refreshing: Boolean = false, val error: PodcastError? = null)

@HiltViewModel
class PodcastViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val podcasts: PodcastRepository,
    private val episodes: EpisodeRepository,
    actions: EpisodeActions,
) : EpisodeListViewModel(actions) {

    private val route = savedStateHandle.toRoute<PodcastRoute>()
    private val feedUrl = MutableStateFlow(route.feedUrl)
    private val status = MutableStateFlow(LoadStatus())
    private val filter = MutableStateFlow(EpisodeFilter.All)

    private val content = feedUrl.flatMapLatest { url ->
        if (url == null) {
            flowOf<Pair<Podcast?, List<Episode>>>(null to emptyList())
        } else {
            combine(podcasts.observePodcast(url), episodes.observeForPodcast(url)) { podcast, list -> podcast to list }
        }
    }

    val uiState: StateFlow<PodcastUiState> = combine(
        content,
        filter,
        status,
        actions.nowPlaying,
        actions.queuedIds,
    ) { (podcast, all), selectedFilter, loadStatus, nowPlaying, queued ->
        PodcastUiState(
            title = podcast?.title ?: route.title,
            artworkUrl = podcast?.artworkUrl ?: route.artworkUrl,
            podcast = podcast,
            episodes = selectedFilter.apply(all),
            totalEpisodes = all.size,
            filter = selectedFilter,
            refreshing = loadStatus.refreshing,
            // A failed background refresh doesn't hide episodes that are already stored.
            error = loadStatus.error,
            nowPlaying = nowPlaying,
            queuedIds = queued,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PodcastUiState(title = route.title, artworkUrl = route.artworkUrl),
    )

    init {
        load(maxAge = STALE_AFTER)
    }

    fun refresh() = load(maxAge = Duration.ZERO)

    fun setFilter(value: EpisodeFilter) {
        filter.value = value
    }

    fun errorShown() {
        status.update { it.copy(error = null) }
    }

    fun toggleSubscribed() {
        val podcast = uiState.value.podcast ?: return
        viewModelScope.launch { podcasts.setSubscribed(podcast.feedUrl, !podcast.subscribed) }
    }

    private fun load(maxAge: Duration) {
        if (status.value.refreshing) return
        viewModelScope.launch {
            status.value = LoadStatus(refreshing = true)
            val url = feedUrl.value ?: resolveFeedUrl()?.also { feedUrl.value = it }
            if (url == null) {
                status.value = LoadStatus(error = PodcastError.FeedNotFound)
                return@launch
            }
            val result = podcasts.refresh(url, maxAge)
            status.value = LoadStatus(error = if (result.isFailure) PodcastError.LoadFailed else null)
        }
    }

    private suspend fun resolveFeedUrl(): String? = suspendRunCatching {
        podcasts.resolveFeedUrl(
            DirectoryPodcast(
                itunesId = route.itunesId,
                title = route.title,
                author = "",
                artworkUrl = route.artworkUrl,
                feedUrl = null,
                genre = null,
            ),
        )
    }.getOrNull()

    private companion object {
        /** Opening a show refetches its feed only if the last fetch is older than this. */
        val STALE_AFTER: Duration = Duration.ofMinutes(30)
    }
}
