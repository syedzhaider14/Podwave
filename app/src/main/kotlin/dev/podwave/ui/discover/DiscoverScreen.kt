package dev.podwave.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.podwave.R
import dev.podwave.model.DirectoryPodcast
import dev.podwave.ui.common.Artwork
import dev.podwave.ui.common.LoadingState
import dev.podwave.ui.common.MessageState

@Composable
fun DiscoverScreen(
    onOpenPodcast: (DirectoryPodcast) -> Unit,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.nav_discover), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = viewModel::clearQuery) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear_search))
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                )
            }
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when (val results = state.results) {
            DirectoryResults.Loading -> LoadingState(modifier)
            DirectoryResults.Failed -> MessageState(
                icon = Icons.Rounded.CloudOff,
                title = stringResource(R.string.directory_error_title),
                body = stringResource(R.string.directory_error_body),
                actionLabel = stringResource(R.string.action_retry),
                onAction = viewModel::retry,
                modifier = modifier,
            )
            is DirectoryResults.Loaded -> if (results.podcasts.isEmpty()) {
                MessageState(
                    icon = Icons.Rounded.SearchOff,
                    title = stringResource(R.string.search_no_results, results.term),
                    modifier = modifier,
                )
            } else {
                LazyColumn(modifier, contentPadding = PaddingValues(bottom = 16.dp)) {
                    item(key = "title") {
                        Text(
                            if (results.term.isEmpty()) stringResource(R.string.top_podcasts) else stringResource(R.string.search_results),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    itemsIndexed(results.podcasts, key = { _, podcast -> podcast.itunesId }) { index, podcast ->
                        DirectoryRow(
                            podcast = podcast,
                            rank = if (results.term.isEmpty()) index + 1 else null,
                            subscribed = podcast.feedUrl != null && podcast.feedUrl in state.subscribedFeeds,
                            onClick = { onOpenPodcast(podcast) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryRow(podcast: DirectoryPodcast, rank: Int?, subscribed: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rank != null) {
            Text(
                rank.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 28.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Artwork(podcast.artworkUrl, contentDescription = null, modifier = Modifier.size(64.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(podcast.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                podcast.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            podcast.genre?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (subscribed) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = stringResource(R.string.subscribed),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
