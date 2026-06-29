package dev.podwave.ui.episode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.podwave.R
import dev.podwave.model.DownloadStatus
import dev.podwave.model.Episode
import dev.podwave.ui.common.Artwork
import dev.podwave.ui.common.DownloadButton
import dev.podwave.ui.common.EpisodeMeta
import dev.podwave.ui.common.LoadingState
import dev.podwave.ui.common.MessageState
import dev.podwave.ui.common.NowPlaying
import dev.podwave.ui.common.durationText
import dev.podwave.ui.common.rememberNotificationPermissionRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeScreen(
    onBack: () -> Unit,
    onOpenPodcast: (String) -> Unit,
    viewModel: EpisodeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val requestNotifications = rememberNotificationPermissionRequest()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.navigate_up))
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when (val current = state) {
            EpisodeUiState.Loading -> LoadingState(modifier)
            EpisodeUiState.Missing -> MessageState(
                icon = Icons.Rounded.SearchOff,
                title = stringResource(R.string.episode_missing),
                modifier = modifier,
            )
            is EpisodeUiState.Loaded -> EpisodeDetails(
                episode = current.episode,
                queued = current.queued,
                nowPlaying = current.nowPlaying,
                onOpenPodcast = { onOpenPodcast(current.episode.podcastFeedUrl) },
                onPlayOrPause = { viewModel.playOrPause(current.episode) },
                onPlayFrom = { viewModel.playFrom(current.episode, it) },
                onToggleQueued = { viewModel.toggleQueued(current.episode, current.queued) },
                onDownload = {
                    if (current.episode.downloadStatus == DownloadStatus.None) requestNotifications()
                    viewModel.onDownloadClicked(current.episode)
                },
                onTogglePlayed = { viewModel.togglePlayed(current.episode) },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun EpisodeDetails(
    episode: Episode,
    queued: Boolean,
    nowPlaying: NowPlaying,
    onOpenPodcast: () -> Unit,
    onPlayOrPause: () -> Unit,
    onPlayFrom: (Long) -> Unit,
    onToggleQueued: () -> Unit,
    onDownload: () -> Unit,
    onTogglePlayed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(episode.artworkUrl, contentDescription = null, modifier = Modifier.size(96.dp), shape = MaterialTheme.shapes.medium)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    episode.podcastTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onOpenPodcast),
                )
                Text(episode.title, style = MaterialTheme.typography.titleLarge)
                val number = listOfNotNull(
                    episode.season?.let { stringResource(R.string.season_number, it) },
                    episode.episodeNumber?.let { stringResource(R.string.episode_number, it) },
                ).joinToString(" · ")
                if (number.isNotEmpty()) {
                    Text(number, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        EpisodeMeta(episode)

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val playing = nowPlaying.isPlayingEpisode(episode)
            Button(onClick = onPlayOrPause, modifier = Modifier.weight(1f)) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                val remaining = episode.remainingMs
                Text(
                    when {
                        playing -> stringResource(R.string.action_pause)
                        episode.isInProgress && remaining != null ->
                            stringResource(R.string.resume_with_time, durationText(remaining))
                        else -> stringResource(R.string.action_play)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggleQueued) {
                Icon(
                    if (queued) Icons.AutoMirrored.Rounded.PlaylistAddCheck else Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = stringResource(if (queued) R.string.action_remove_from_queue else R.string.action_add_to_queue),
                    tint = if (queued) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DownloadButton(episode, onClick = onDownload)
            IconButton(onClick = onTogglePlayed) {
                Icon(
                    if (episode.isPlayed) Icons.Rounded.CheckCircle else Icons.Rounded.CheckCircleOutline,
                    contentDescription = stringResource(
                        if (episode.isPlayed) R.string.action_mark_unplayed else R.string.action_mark_played,
                    ),
                    tint = if (episode.isPlayed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider()

        if (episode.description.isBlank()) {
            Text(
                stringResource(R.string.no_show_notes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ShowNotes(
                notes = episode.description,
                durationMs = episode.durationMs,
                onTimestampClick = onPlayFrom,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}
