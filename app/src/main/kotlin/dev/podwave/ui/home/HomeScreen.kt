package dev.podwave.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.podwave.R
import dev.podwave.model.Episode
import dev.podwave.ui.common.Artwork
import dev.podwave.ui.common.EpisodeRow
import dev.podwave.ui.common.EpisodeRowCallbacks
import dev.podwave.ui.common.LoadingState
import dev.podwave.ui.common.MessageState
import dev.podwave.ui.common.NowPlaying
import dev.podwave.ui.common.durationText
import dev.podwave.ui.common.rememberEpisodeRowCallbacks

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenEpisode: (String) -> Unit,
    onDiscover: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val callbacks = rememberEpisodeRowCallbacks(viewModel) { onOpenEpisode(it.id) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.home_title)) }, scrollBehavior = scrollBehavior)
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.loading -> LoadingState()
                state.isEmpty -> MessageState(
                    icon = Icons.Rounded.Podcasts,
                    title = stringResource(if (state.hasSubscriptions) R.string.home_caught_up_title else R.string.home_empty_title),
                    body = stringResource(if (state.hasSubscriptions) R.string.home_caught_up_body else R.string.home_empty_body),
                    actionLabel = stringResource(R.string.discover_podcasts),
                    onAction = onDiscover,
                )
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    if (state.continueListening.isNotEmpty()) {
                        sectionHeader(R.string.continue_listening)
                        item(key = "continue") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.continueListening, key = { it.id }) { episode ->
                                    ContinueCard(
                                        episode = episode,
                                        playing = state.nowPlaying.isPlayingEpisode(episode),
                                        onOpen = { onOpenEpisode(episode.id) },
                                        onPlayPause = { viewModel.playOrPause(episode) },
                                    )
                                }
                            }
                        }
                    }

                    if (state.queue.isNotEmpty()) {
                        sectionHeader(R.string.up_next) {
                            TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.action_clear)) }
                        }
                        items(state.queue, key = { "queue-${it.id}" }) { episode ->
                            QueueItem(
                                episode = episode,
                                nowPlaying = state.nowPlaying,
                                callbacks = callbacks,
                                onRemove = { viewModel.removeFromQueue(episode) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    if (state.latest.isNotEmpty()) {
                        sectionHeader(R.string.new_episodes)
                        items(state.latest, key = { "latest-${it.id}" }) { episode ->
                            EpisodeRow(
                                episode = episode,
                                queued = false,
                                nowPlaying = state.nowPlaying,
                                callbacks = callbacks,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_queue_title)) },
            text = { Text(pluralStringResource(R.plurals.clear_queue_body, state.queue.size, state.queue.size)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; viewModel.clearQueue() }) {
                    Text(stringResource(R.string.action_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun LazyListScope.sectionHeader(@StringRes title: Int, action: (@Composable () -> Unit)? = null) {
    item(key = "header-$title", contentType = "header") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            action?.invoke()
        }
    }
}

@Composable
private fun ContinueCard(episode: Episode, playing: Boolean, onOpen: () -> Unit, onPlayPause: () -> Unit) {
    Column(Modifier.width(148.dp).clickable(onClick = onOpen)) {
        Box {
            Artwork(episode.artworkUrl, contentDescription = null, modifier = Modifier.size(148.dp), shape = MaterialTheme.shapes.medium)
            SmallFloatingActionButton(
                onClick = onPlayPause,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            ) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.action_pause else R.string.action_resume),
                )
            }
        }
        val duration = episode.durationMs
        if (duration != null && duration > 0) {
            LinearProgressIndicator(
                progress = { (episode.positionMs.toFloat() / duration).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        Text(
            episode.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        episode.remainingMs?.let { remaining ->
            Text(
                stringResource(R.string.time_left, durationText(remaining)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Swipe left to take an episode out of the queue. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueItem(
    episode: Episode,
    nowPlaying: NowPlaying,
    callbacks: EpisodeRowCallbacks,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.action_remove_from_queue),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Surface {
            EpisodeRow(episode = episode, queued = true, nowPlaying = nowPlaying, callbacks = callbacks)
        }
    }
}
