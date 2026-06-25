package dev.podwave.ui.library

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.podwave.R
import dev.podwave.model.Podcast
import dev.podwave.ui.common.Artwork
import dev.podwave.ui.common.EpisodeRow
import dev.podwave.ui.common.LoadingState
import dev.podwave.ui.common.MessageState
import dev.podwave.ui.common.rememberEpisodeRowCallbacks

private enum class LibraryTab(@StringRes val label: Int) {
    Shows(R.string.library_shows),
    Downloads(R.string.library_downloads),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenPodcast: (String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    onDiscover: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val callbacks = rememberEpisodeRowCallbacks(viewModel) { onOpenEpisode(it.id) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text(stringResource(R.string.nav_library)) })
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    LibraryTab.entries.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when {
            state.loading -> LoadingState(modifier)
            LibraryTab.entries[selectedTab] == LibraryTab.Shows -> ShowsGrid(state.subscriptions, onOpenPodcast, onDiscover, modifier)
            state.downloads.isEmpty() -> MessageState(
                icon = Icons.Rounded.DownloadForOffline,
                title = stringResource(R.string.downloads_empty_title),
                body = stringResource(R.string.downloads_empty_body),
                modifier = modifier,
            )
            else -> LazyColumn(modifier, contentPadding = PaddingValues(bottom = 16.dp)) {
                item(key = "summary") {
                    val size = Formatter.formatShortFileSize(LocalContext.current, state.downloadsSizeBytes)
                    Text(
                        pluralStringResource(R.plurals.downloads_summary, state.downloads.size, state.downloads.size, size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                items(state.downloads, key = { it.id }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        queued = episode.id in state.queuedIds,
                        nowPlaying = state.nowPlaying,
                        callbacks = callbacks,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShowsGrid(
    podcasts: List<Podcast>,
    onOpenPodcast: (String) -> Unit,
    onDiscover: () -> Unit,
    modifier: Modifier,
) {
    if (podcasts.isEmpty()) {
        MessageState(
            icon = Icons.Rounded.Subscriptions,
            title = stringResource(R.string.library_empty_title),
            body = stringResource(R.string.library_empty_body),
            actionLabel = stringResource(R.string.discover_podcasts),
            onAction = onDiscover,
            modifier = modifier,
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 112.dp),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(podcasts, key = { it.feedUrl }) { podcast ->
            Column(Modifier.clickable { onOpenPodcast(podcast.feedUrl) }) {
                Artwork(
                    podcast.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    shape = MaterialTheme.shapes.medium,
                )
                Text(
                    podcast.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
