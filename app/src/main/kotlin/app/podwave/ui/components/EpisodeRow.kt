package app.podwave.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.podwave.domain.DownloadState
import app.podwave.domain.Episode
import app.podwave.rss.formatDuration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val DateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

fun Episode.metaLine(): String = listOfNotNull(
    publishedAt?.atZone(ZoneId.systemDefault())?.format(DateFormat),
    durationSeconds?.let { seconds ->
        if (isInProgress) {
            "${formatDuration(seconds - playbackPositionMs / 1000)} left"
        } else {
            formatDuration(seconds)
        }
    },
).joinToString(" · ")

@Composable
fun EpisodeRow(
    episode: Episode,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    showPodcast: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(url = episode.artworkUrl, modifier = Modifier.size(56.dp), cornerRadius = 8.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (showPodcast) {
                Text(
                    episode.podcastTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                episode.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (episode.completed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (episode.completed) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = "Played",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    episode.metaLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            episode.progress?.takeIf { episode.isInProgress }?.let { progress ->
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
            }
        }
        DownloadButton(episode, onDownload)
        IconButton(onClick = onQueue) {
            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Add to queue")
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play")
        }
    }
}

@Composable
private fun DownloadButton(episode: Episode, onDownload: () -> Unit) {
    when (episode.downloadState) {
        DownloadState.Downloaded -> Icon(
            Icons.Rounded.DownloadDone,
            contentDescription = "Downloaded",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        DownloadState.Queued, DownloadState.Downloading -> CircularProgressIndicator(
            progress = { episode.downloadProgress / 100f },
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .size(24.dp),
            strokeWidth = 3.dp,
        )
        DownloadState.None, DownloadState.Failed -> IconButton(onClick = onDownload) {
            Icon(
                Icons.Rounded.Download,
                contentDescription = if (episode.downloadState == DownloadState.Failed) "Retry download" else "Download",
                tint = if (episode.downloadState == DownloadState.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
