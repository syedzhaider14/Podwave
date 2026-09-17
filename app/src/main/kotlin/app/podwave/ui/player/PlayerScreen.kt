package app.podwave.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podwave.domain.SleepTimerState
import app.podwave.rss.formatDuration
import app.podwave.ui.components.Artwork
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onClose: () -> Unit,
    viewModel: PlayerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sleep by viewModel.sleepTimer.collectAsStateWithLifecycle()
    val sleepRemaining by viewModel.sleepRemainingMs.collectAsStateWithLifecycle(initialValue = null)

    // While dragging, show the drag position instead of the (lagging) player position.
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    val fraction = if (scrubbing) scrubFraction else state.progress
    val shownPositionMs = if (scrubbing) (state.durationMs * scrubFraction).toLong() else state.positionMs

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close player")
                    }
                },
            )
        },
    ) { padding ->
        if (!state.hasMedia) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing playing")
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Artwork(url = state.artworkUrl, modifier = Modifier.fillMaxWidth(0.85f), cornerRadius = 20.dp)
            Text(state.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(state.podcastTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)

            Column(Modifier.fillMaxWidth()) {
                Slider(
                    value = fraction,
                    onValueChange = {
                        scrubbing = true
                        scrubFraction = it
                    },
                    onValueChangeFinished = {
                        viewModel.seekTo(scrubFraction)
                        scrubbing = false
                    },
                    modifier = Modifier.semantics { contentDescription = "Playback position" },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDuration(shownPositionMs / 1000), style = MaterialTheme.typography.labelSmall)
                    Text(
                        "-" + formatDuration((state.durationMs - shownPositionMs).coerceAtLeast(0) / 1000),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::seekBack) {
                    Icon(Icons.Rounded.Replay10, contentDescription = "Back 10 seconds", modifier = Modifier.size(32.dp))
                }
                FilledIconButton(onClick = viewModel::togglePlayPause, modifier = Modifier.size(72.dp)) {
                    if (state.isBuffering) {
                        CircularProgressIndicator(Modifier.size(32.dp))
                    } else {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                IconButton(onClick = viewModel::seekForward) {
                    Icon(Icons.Rounded.Forward30, contentDescription = "Forward 30 seconds", modifier = Modifier.size(32.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SpeedMenu(current = state.speed, speeds = viewModel.speeds, onSelect = viewModel::setSpeed)
                SleepMenu(
                    state = sleep,
                    remainingMs = sleepRemaining,
                    onMinutes = viewModel::startSleepTimer,
                    onEndOfEpisode = viewModel::sleepAtEndOfEpisode,
                    onCancel = viewModel::cancelSleepTimer,
                )
                IconButton(onClick = viewModel::skipToNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next in queue")
                }
            }
        }
    }
}

@Composable
private fun SpeedMenu(current: Float, speeds: List<Float>, onSelect: (Float) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("${formatSpeed(current)}×") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            speeds.forEach { speed ->
                DropdownMenuItem(
                    text = { Text("${formatSpeed(speed)}×") },
                    onClick = {
                        open = false
                        onSelect(speed)
                    },
                )
            }
        }
    }
}

@Composable
private fun SleepMenu(
    state: SleepTimerState,
    remainingMs: Long?,
    onMinutes: (Int) -> Unit,
    onEndOfEpisode: () -> Unit,
    onCancel: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.Rounded.Bedtime, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
            Text(
                when (state) {
                    SleepTimerState.Off -> "Sleep"
                    SleepTimerState.EndOfEpisode -> "End of episode"
                    is SleepTimerState.Countdown -> formatDuration((remainingMs ?: 0) / 1000)
                },
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(5, 15, 30, 45, 60).forEach { minutes ->
                DropdownMenuItem(
                    text = { Text("$minutes minutes") },
                    onClick = {
                        open = false
                        onMinutes(minutes)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("End of episode") },
                onClick = {
                    open = false
                    onEndOfEpisode()
                },
            )
            if (state != SleepTimerState.Off) {
                DropdownMenuItem(
                    text = { Text("Turn off") },
                    onClick = {
                        open = false
                        onCancel()
                    },
                )
            }
        }
    }
}

internal fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else "%.1f".format(speed).trimEnd('0').trimEnd('.')
