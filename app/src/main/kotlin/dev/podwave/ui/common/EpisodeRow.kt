package dev.podwave.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.podwave.R
import dev.podwave.model.DownloadStatus
import dev.podwave.model.Episode

@Immutable
data class EpisodeRowCallbacks(
    val onOpen: (Episode) -> Unit,
    val onPlayOrPause: (Episode) -> Unit,
    val onDownload: (Episode) -> Unit,
    val onToggleQueued: (Episode, Boolean) -> Unit,
    val onPlayNext: (Episode) -> Unit,
    val onTogglePlayed: (Episode) -> Unit,
)

@Composable
fun rememberEpisodeRowCallbacks(viewModel: EpisodeListViewModel, onOpen: (Episode) -> Unit): EpisodeRowCallbacks {
    val requestNotifications = rememberNotificationPermissionRequest()
    return remember(viewModel, onOpen, requestNotifications) {
        EpisodeRowCallbacks(
            onOpen = onOpen,
            onPlayOrPause = viewModel::playOrPause,
            onDownload = { episode ->
                if (episode.downloadStatus == DownloadStatus.None) requestNotifications()
                viewModel.onDownloadClicked(episode)
            },
            onToggleQueued = viewModel::toggleQueued,
            onPlayNext = viewModel::playNext,
            onTogglePlayed = viewModel::togglePlayed,
        )
    }
}

@Composable
fun EpisodeRow(
    episode: Episode,
    queued: Boolean,
    nowPlaying: NowPlaying,
    callbacks: EpisodeRowCallbacks,
    modifier: Modifier = Modifier,
    showArtwork: Boolean = true,
    showPodcastTitle: Boolean = true,
) {
    val isCurrent = nowPlaying.isCurrent(episode)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { callbacks.onOpen(episode) }
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showArtwork) {
            Artwork(episode.artworkUrl, contentDescription = null, modifier = Modifier.size(56.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            if (showPodcastTitle) {
                Text(
                    episode.podcastTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                episode.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (episode.isPlayed) FontWeight.Normal else FontWeight.SemiBold,
                color = when {
                    isCurrent -> MaterialTheme.colorScheme.primary
                    episode.isPlayed -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            EpisodeMeta(episode, Modifier.padding(top = 2.dp))
            val duration = episode.durationMs
            if (episode.isInProgress && duration != null && duration > 0) {
                LinearProgressIndicator(
                    progress = { (episode.positionMs.toFloat() / duration).coerceIn(0f, 1f) },
                    modifier = Modifier.padding(top = 6.dp).width(96.dp),
                )
            }
        }
        IconButton(onClick = { callbacks.onPlayOrPause(episode) }) {
            val playing = nowPlaying.isPlayingEpisode(episode)
            Icon(
                if (playing) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle,
                contentDescription = stringResource(if (playing) R.string.action_pause else R.string.action_play),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        DownloadButton(episode, onClick = { callbacks.onDownload(episode) })
        EpisodeMenu(episode, queued, callbacks)
    }
}

@Composable
fun EpisodeMeta(episode: Episode, modifier: Modifier = Modifier) {
    val parts = buildList {
        episode.publishedAt?.let { add(publishedText(it)) }
        val remaining = episode.remainingMs
        when {
            episode.isPlayed -> add(stringResource(R.string.episode_played))
            episode.isInProgress && remaining != null -> add(stringResource(R.string.time_left, durationText(remaining)))
            episode.durationMs != null -> add(durationText(episode.durationMs))
        }
        if (episode.explicit) add(stringResource(R.string.episode_explicit))
    }
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun DownloadButton(episode: Episode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var confirmRemove by remember { mutableStateOf(false) }

    IconButton(
        onClick = { if (episode.downloadStatus == DownloadStatus.Downloaded) confirmRemove = true else onClick() },
        modifier = modifier,
    ) {
        when (episode.downloadStatus) {
            DownloadStatus.None -> Icon(Icons.Rounded.Download, stringResource(R.string.action_download))
            DownloadStatus.Queued -> Icon(Icons.Rounded.Schedule, stringResource(R.string.download_waiting))
            DownloadStatus.Downloading -> Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { episode.downloadProgress / 100f },
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_cancel_download),
                    modifier = Modifier.size(14.dp),
                )
            }
            DownloadStatus.Downloaded -> Icon(
                Icons.Rounded.DownloadDone,
                contentDescription = stringResource(R.string.download_done),
                tint = MaterialTheme.colorScheme.primary,
            )
            DownloadStatus.Failed -> Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = stringResource(R.string.download_failed_retry),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.remove_download_title)) },
            text = { Text(stringResource(R.string.remove_download_body)) },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; onClick() }) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun EpisodeMenu(episode: Episode, queued: Boolean, callbacks: EpisodeRowCallbacks) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_play_next)) },
                onClick = { expanded = false; callbacks.onPlayNext(episode) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (queued) R.string.action_remove_from_queue else R.string.action_add_to_queue)) },
                onClick = { expanded = false; callbacks.onToggleQueued(episode, queued) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (episode.isPlayed) R.string.action_mark_unplayed else R.string.action_mark_played)) },
                onClick = { expanded = false; callbacks.onTogglePlayed(episode) },
            )
        }
    }
}
