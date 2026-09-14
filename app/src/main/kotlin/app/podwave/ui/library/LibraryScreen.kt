package app.podwave.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podwave.ui.components.Artwork
import app.podwave.ui.components.EmptyState
import app.podwave.ui.components.EpisodeRow
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onPodcastClick: (Long) -> Unit,
    onEpisodeClick: (Long) -> Unit,
    onDiscoverClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = onSettingsClick) { Icon(Icons.Rounded.Settings, contentDescription = "Settings") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loaded && state.subscriptions.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Podcasts,
                title = "No podcasts yet",
                message = "Find shows in Discover, add one by its RSS link, or import an OPML file in Settings.",
                actionLabel = "Discover podcasts",
                onAction = onDiscoverClick,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                item(key = "subscriptions") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.subscriptions, key = { it.id }) { podcast ->
                            Column(
                                Modifier
                                    .width(96.dp)
                                    .clickable { onPodcastClick(podcast.id) },
                            ) {
                                Artwork(url = podcast.artworkUrl)
                                Text(
                                    podcast.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
                item(key = "filters") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(LibraryFilter.entries) { filter ->
                            FilterChip(
                                selected = state.filter == filter,
                                onClick = { viewModel.setFilter(filter) },
                                label = { Text(filter.label) },
                            )
                        }
                    }
                }
                if (state.episodes.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            when (state.filter) {
                                LibraryFilter.Latest -> "No episodes yet. Pull to refresh."
                                LibraryFilter.InProgress -> "Nothing in progress."
                                LibraryFilter.Downloaded -> "No downloads. Tap the download icon on any episode."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                items(state.episodes, key = { it.id }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        onClick = { onEpisodeClick(episode.id) },
                        onPlay = { viewModel.play(episode) },
                        onQueue = { viewModel.enqueue(episode) },
                        onDownload = { viewModel.toggleDownload(episode) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}
