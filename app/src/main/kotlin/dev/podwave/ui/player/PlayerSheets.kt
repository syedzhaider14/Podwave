package dev.podwave.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.podwave.R
import dev.podwave.model.AppSettings
import dev.podwave.playback.SleepTimerOption
import dev.podwave.playback.SleepTimerState
import dev.podwave.ui.common.formatClock
import dev.podwave.ui.common.formatSpeed
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(current: Float, onSelect: (Float) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            SheetTitle(stringResource(R.string.playback_speed))
            Column(Modifier.selectableGroup()) {
                AppSettings.PlaybackSpeeds.forEach { speed ->
                    OptionRow(
                        label = formatSpeed(speed),
                        selected = abs(speed - current) < 0.01f,
                        onClick = { onSelect(speed) },
                    )
                }
            }
            Text(
                stringResource(R.string.playback_speed_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    state: SleepTimerState,
    remainingMs: Long?,
    onSelect: (SleepTimerOption) -> Unit,
    onExtend: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            SheetTitle(stringResource(R.string.sleep_timer))

            if (state is SleepTimerState.Countdown) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.sleep_remaining, formatClock(remainingMs ?: 0)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedButton(onClick = { onExtend(EXTEND_MINUTES) }) {
                        Text(stringResource(R.string.sleep_extend, EXTEND_MINUTES))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }

            Column(Modifier.selectableGroup()) {
                SleepTimerOption.Presets.forEach { option ->
                    OptionRow(
                        label = when (option) {
                            is SleepTimerOption.Minutes -> pluralStringResource(R.plurals.sleep_minutes, option.minutes, option.minutes)
                            SleepTimerOption.EndOfEpisode -> stringResource(R.string.sleep_end_of_episode)
                            SleepTimerOption.Off -> stringResource(R.string.sleep_off)
                        },
                        selected = option == SleepTimerOption.EndOfEpisode && state == SleepTimerState.EndOfEpisode,
                        onClick = { onSelect(option) },
                    )
                }
            }

            if (state != SleepTimerState.Off) {
                TextButton(
                    onClick = { onSelect(SleepTimerOption.Off) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Text(stringResource(R.string.sleep_turn_off))
                }
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

private const val EXTEND_MINUTES = 5
