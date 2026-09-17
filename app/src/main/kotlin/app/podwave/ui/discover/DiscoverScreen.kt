package app.podwave.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podwave.data.remote.normalizeFeedUrl
import app.podwave.domain.DirectoryPodcast
import app.podwave.ui.components.Artwork
import app.podwave.ui.components.EmptyState
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onOpenPodcast: (Long) -> Unit,
    viewModel: DiscoverViewModel = koinViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val subscribed by viewModel.subscribedFeeds.collectAsStateWithLifecycle()
    val subscribing by viewModel.subscribing.collectAsStateWithLifecycle()
    var addByUrl by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is DiscoverEvent.OpenPodcast -> onOpenPodcast(event.podcastId)
                is DiscoverEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Discover") },
                actions = {
                    IconButton(onClick = { addByUrl = true }) {
                        Icon(Icons.Rounded.Link, contentDescription = "Add by RSS link")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Search podcasts") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when (val current = search) {
                SearchState.Idle -> EmptyState(
                    icon = Icons.Rounded.TravelExplore,
                    title = "Find something to listen to",
                    message = "Search by show name, host or topic.",
                )
                SearchState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is SearchState.Error -> EmptyState(
                    icon = Icons.Rounded.CloudOff,
                    title = "Couldn't search",
                    message = current.message,
                    actionLabel = "Retry",
                    onAction = viewModel::retry,
                )
                is SearchState.Results -> if (current.podcasts.isEmpty()) {
                    EmptyState(icon = Icons.Rounded.Search, title = "No results", message = "Try a different search.")
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(current.podcasts, key = { it.feedUrl }) { podcast ->
                            DirectoryRow(
                                podcast = podcast,
                                isSubscribed = podcast.feedUrl in subscribed,
                                isSubscribing = normalizeFeedUrl(podcast.feedUrl) in subscribing,
                                onSubscribe = { viewModel.subscribe(podcast.feedUrl) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (addByUrl) {
        AddByUrlDialog(
            onDismiss = { addByUrl = false },
            onAdd = { url ->
                addByUrl = false
                viewModel.subscribe(url)
            },
        )
    }
}

@Composable
private fun DirectoryRow(
    podcast: DirectoryPodcast,
    isSubscribed: Boolean,
    isSubscribing: Boolean,
    onSubscribe: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(url = podcast.artworkUrl, modifier = Modifier.size(64.dp), cornerRadius = 8.dp)
        Column(Modifier.weight(1f)) {
            Text(podcast.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            podcast.author?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                listOfNotNull(podcast.genre, podcast.episodeCount?.let { "$it episodes" }).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            isSubscribed -> AssistChip(
                onClick = {},
                label = { Text("Subscribed") },
                leadingIcon = { Icon(Icons.Rounded.Check, contentDescription = null) },
            )
            isSubscribing -> CircularProgressIndicator(Modifier.size(24.dp))
            else -> FilledTonalButton(onClick = onSubscribe) { Text("Subscribe") }
        }
    }
}

@Composable
private fun AddByUrlDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add by RSS link") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = { Text("https://example.com/feed.xml") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            )
        },
        confirmButton = { TextButton(onClick = { onAdd(url) }, enabled = url.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
