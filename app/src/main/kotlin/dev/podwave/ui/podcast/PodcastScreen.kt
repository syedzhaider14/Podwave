package dev.podwave.ui.podcast

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
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
import dev.podwave.ui.common.Html
import dev.podwave.ui.common.LoadingState
import dev.podwave.ui.common.MessageState
import dev.podwave.ui.common.rememberEpisodeRowCallbacks

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastScreen(
    onBack: () -> Unit,
    onOpenEpisode: (String) -> Unit,
    viewModel: PodcastViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val callbacks = rememberEpisodeRowCallbacks(viewModel) { onOpenEpisode(it.id) }
    val listState = rememberLazyListState()
    val showTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val snackbar = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

    val refreshFailedMessage = stringResource(R.string.podcast_refresh_failed)
    LaunchedEffect(state.error, state.podcast != null) {
        // With stored episodes on screen, a failed refresh is a footnote, not a full-screen error.
        if (state.error == PodcastError.LoadFailed && state.podcast != null) {
            snackbar.showSnackbar(refreshFailedMessage)
            viewModel.errorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showTitle) Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.navigate_up))
                    }
                },
                actions = {
                    state.podcast?.websiteUrl?.let { url ->
                        IconButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                            Icon(Icons.Rounded.Public, contentDescription = stringResource(R.string.open_website))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when {
            state.podcast == null && state.error == PodcastError.FeedNotFound -> MessageState(
                icon = Icons.Rounded.SearchOff,
                title = stringResource(R.string.podcast_not_found_title),
                body = stringResource(R.string.podcast_not_found_body),
                actionLabel = stringResource(R.string.action_retry),
                onAction = viewModel::refresh,
                modifier = modifier,
            )
            state.podcast == null && state.error == PodcastError.LoadFailed -> MessageState(
                icon = Icons.Rounded.CloudOff,
                title = stringResource(R.string.podcast_load_failed_title),
                body = stringResource(R.string.podcast_load_failed_body),
                actionLabel = stringResource(R.string.action_retry),
                onAction = viewModel::refresh,
                modifier = modifier,
            )
            state.loading -> LoadingState(modifier)
            else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = modifier) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    state.podcast?.let { podcast ->
                        item(key = "header") {
                            PodcastHeader(podcast, onToggleSubscribed = viewModel::toggleSubscribed)
                        }
                    }
                    item(key = "filters") {
                        FilterRow(
                            selected = state.filter,
                            total = state.totalEpisodes,
                            onSelect = viewModel::setFilter,
                        )
                    }
                    if (state.episodes.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                stringResource(R.string.podcast_filter_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                    items(state.episodes, key = { it.id }) { episode ->
                        EpisodeRow(
                            episode = episode,
                            queued = episode.id in state.queuedIds,
                            nowPlaying = state.nowPlaying,
                            callbacks = callbacks,
                            showArtwork = false,
                            showPodcastTitle = false,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PodcastHeader(podcast: Podcast, onToggleSubscribed: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val description = remember(podcast.description) { Html.toPlainText(podcast.description) }

    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(podcast.artworkUrl, contentDescription = null, modifier = Modifier.size(120.dp), shape = MaterialTheme.shapes.medium)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(podcast.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (podcast.author.isNotBlank()) {
                    Text(
                        podcast.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (podcast.categories.isNotEmpty()) {
                    Text(
                        podcast.categories.take(3).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        if (podcast.subscribed) {
            FilledTonalButton(onClick = onToggleSubscribed) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.subscribed))
            }
        } else {
            Button(onClick = onToggleSubscribed) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.subscribe))
            }
        }

        if (description.isNotEmpty()) {
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .animateContentSize()
                    .clickable(onClickLabel = stringResource(if (expanded) R.string.show_less else R.string.show_more)) {
                        expanded = !expanded
                    },
            )
        }
    }
}

@Composable
private fun FilterRow(selected: EpisodeFilter, total: Int, onSelect: (EpisodeFilter) -> Unit) {
    Column(Modifier.padding(top = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.FilterList, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(
                pluralStringResource(R.plurals.episode_count, total, total),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EpisodeFilter.entries.forEach { filter ->
                FilterChip(
                    selected = filter == selected,
                    onClick = { onSelect(filter) },
                    label = {
                        Text(
                            stringResource(
                                when (filter) {
                                    EpisodeFilter.All -> R.string.filter_all
                                    EpisodeFilter.Unplayed -> R.string.filter_unplayed
                                    EpisodeFilter.Downloaded -> R.string.filter_downloaded
                                },
                            ),
                        )
                    },
                )
            }
        }
    }
}
