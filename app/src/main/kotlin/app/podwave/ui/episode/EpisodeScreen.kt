package app.podwave.ui.episode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podwave.domain.DownloadState
import app.podwave.domain.Episode
import app.podwave.ui.components.Artwork
import app.podwave.ui.components.metaLine
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeScreen(
    onBack: () -> Unit,
    viewModel: EpisodeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (val current = state) {
            EpisodeUiState.Loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            EpisodeUiState.Missing -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This episode is no longer available.")
            }
            is EpisodeUiState.Ready -> EpisodeContent(current.episode, viewModel, Modifier.padding(padding))
        }
    }
}

@Composable
private fun EpisodeContent(episode: Episode, viewModel: EpisodeViewModel, modifier: Modifier) {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
    )
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(url = episode.artworkUrl, modifier = Modifier.size(96.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(episode.podcastTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(episode.title, style = MaterialTheme.typography.titleLarge)
                Text(episode.metaLine(), style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = viewModel::play, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Text(if (episode.isInProgress) "Resume" else "Play", modifier = Modifier.padding(start = 8.dp))
            }
            FilledTonalIconButton(onClick = viewModel::playNext) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = "Play next")
            }
            FilledTonalIconButton(onClick = viewModel::enqueue) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Add to queue")
            }
            when (episode.downloadState) {
                DownloadState.Downloaded -> FilledTonalIconButton(onClick = viewModel::deleteDownload) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete download")
                }
                DownloadState.Queued, DownloadState.Downloading -> CircularProgressIndicator(
                    progress = { episode.downloadProgress / 100f },
                    modifier = Modifier.size(32.dp),
                )
                else -> FilledTonalIconButton(onClick = viewModel::toggleDownload) {
                    Icon(Icons.Rounded.Download, contentDescription = "Download")
                }
            }
            FilledTonalIconButton(onClick = viewModel::togglePlayed) {
                Icon(
                    if (episode.completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (episode.completed) "Mark as unplayed" else "Mark as played",
                )
            }
        }

        episode.description?.let {
            Text(
                text = AnnotatedString.fromHtml(it, linkStyles = linkStyles),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
