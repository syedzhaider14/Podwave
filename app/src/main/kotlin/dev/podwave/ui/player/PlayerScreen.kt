package dev.podwave.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.podwave.R
import dev.podwave.model.Episode
import dev.podwave.playback.PlayerState
import dev.podwave.playback.SleepTimerState
import dev.podwave.ui.common.Artwork
import dev.podwave.ui.common.MessageState
import dev.podwave.ui.common.formatClock
import dev.podwave.ui.common.formatSpeed

private enum class PlayerSheet { Speed, SleepTimer }

@Composable
fun PlayerScreen(
    onClose: () -> Unit,
    onOpenEpisode: (String) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val skip by viewModel.skipIntervals.collectAsStateWithLifecycle()
    val upNext by viewModel.upNext.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<PlayerSheet?>(null) }

    PlayerContent(
        state = state,
        skip = skip,
        upNext = upNext,
        onClose = onClose,
        onShowNotes = { state.episodeId?.let(onOpenEpisode) },
        onPlayPause = viewModel::togglePlayPause,
        onSeek = viewModel::seekTo,
        onSkipBack = viewModel::skipBack,
        onSkipForward = viewModel::skipForward,
        onSkipToNext = viewModel::skipToNext,
        onSpeedClick = { sheet = PlayerSheet.Speed },
        onSleepTimerClick = { sheet = PlayerSheet.SleepTimer },
    )

    when (sheet) {
        PlayerSheet.Speed -> SpeedSheet(
            current = state.speed,
            onSelect = { viewModel.setSpeed(it); sheet = null },
            onDismiss = { sheet = null },
        )
        PlayerSheet.SleepTimer -> SleepTimerSheet(
            state = state.sleepTimer,
            remainingMs = state.sleepRemainingMs,
            onSelect = { viewModel.setSleepTimer(it); sheet = null },
            onExtend = { viewModel.extendSleepTimer(it); sheet = null },
            onDismiss = { sheet = null },
        )
        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerContent(
    state: PlayerState,
    skip: SkipIntervals,
    upNext: Episode?,
    onClose: () -> Unit,
    onShowNotes: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipToNext: () -> Unit,
    onSpeedClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        TopAppBar(
            title = { Text(stringResource(R.string.now_playing)) },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.close_player))
                }
            },
            actions = {
                if (state.hasMedia) {
                    IconButton(onClick = onShowNotes) {
                        Icon(Icons.AutoMirrored.Rounded.Notes, contentDescription = stringResource(R.string.show_notes))
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        if (!state.hasMedia) {
            MessageState(icon = Icons.Rounded.Headphones, title = stringResource(R.string.nothing_playing))
            return@Column
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Artwork(
                state.artworkUrl,
                contentDescription = null,
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(1f),
                shape = MaterialTheme.shapes.large,
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    state.podcastTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.hasError) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.player_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Scrubber(positionMs = state.positionMs, durationMs = state.durationMs, onSeek = onSeek)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onSkipBack, modifier = Modifier.size(56.dp)) {
                    SkipIcon(
                        seconds = skip.backSeconds,
                        forward = false,
                        contentDescription = stringResource(R.string.skip_back_seconds, skip.backSeconds),
                        size = 36.dp,
                    )
                }
                FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(80.dp)) {
                    if (state.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 3.dp,
                        )
                    } else {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play),
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }
                IconButton(onClick = onSkipForward, modifier = Modifier.size(56.dp)) {
                    SkipIcon(
                        seconds = skip.forwardSeconds,
                        forward = true,
                        contentDescription = stringResource(R.string.skip_forward_seconds, skip.forwardSeconds),
                        size = 36.dp,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onSpeedClick) {
                        Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(formatSpeed(state.speed))
                    }
                    TextButton(onClick = onSleepTimerClick) {
                        Icon(Icons.Rounded.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(sleepTimerLabel(state.sleepTimer, state.sleepRemainingMs))
                    }
                }
                if (upNext != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.up_next_title, upNext.title),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        IconButton(onClick = onSkipToNext) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.skip_to_next))
                        }
                    }
                }
            }
        }
    }
}

/** The slider follows the finger while dragging and only seeks when it's released. */
@Composable
private fun Scrubber(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownPosition = dragFraction?.let { (it * durationMs).toLong() } ?: positionMs

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { onSeek((it * durationMs).toLong()) }
                dragFraction = null
            },
            enabled = durationMs > 0,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatClock(shownPosition), style = MaterialTheme.typography.labelMedium)
            Text(
                if (durationMs > 0) "-" + formatClock(durationMs - shownPosition) else "--:--",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun sleepTimerLabel(state: SleepTimerState, remainingMs: Long?): String = when (state) {
    SleepTimerState.Off -> stringResource(R.string.sleep_timer)
    SleepTimerState.EndOfEpisode -> stringResource(R.string.sleep_end_of_episode)
    is SleepTimerState.Countdown -> formatClock(remainingMs ?: 0)
}
