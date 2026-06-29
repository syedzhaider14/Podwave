package dev.podwave.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.podwave.BuildConfig
import dev.podwave.R
import dev.podwave.model.AppSettings
import dev.podwave.model.ThemeMode
import dev.podwave.ui.common.LoadingState
import dev.podwave.ui.common.formatSpeed
import kotlinx.coroutines.launch

private enum class SettingsDialog { Speed, SkipBack, SkipForward, Theme }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val refreshingMessage = stringResource(R.string.settings_refresh_started)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = settings
        if (current == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.settings_playback))
            ValueRow(
                title = stringResource(R.string.settings_default_speed),
                value = formatSpeed(current.playbackSpeed),
                onClick = { dialog = SettingsDialog.Speed },
            )
            ValueRow(
                title = stringResource(R.string.settings_skip_back),
                value = secondsLabel(current.skipBackSeconds),
                onClick = { dialog = SettingsDialog.SkipBack },
            )
            ValueRow(
                title = stringResource(R.string.settings_skip_forward),
                value = secondsLabel(current.skipForwardSeconds),
                onClick = { dialog = SettingsDialog.SkipForward },
            )

            SectionHeader(stringResource(R.string.settings_downloads))
            SwitchRow(
                title = stringResource(R.string.settings_auto_download),
                summary = stringResource(R.string.settings_auto_download_summary),
                checked = current.autoDownloadNewEpisodes,
                onCheckedChange = viewModel::setAutoDownload,
            )
            SwitchRow(
                title = stringResource(R.string.settings_wifi_only),
                summary = stringResource(R.string.settings_wifi_only_summary),
                checked = current.downloadOnWifiOnly,
                onCheckedChange = viewModel::setWifiOnly,
            )

            SectionHeader(stringResource(R.string.settings_library))
            ValueRow(
                title = stringResource(R.string.settings_refresh_now),
                value = stringResource(R.string.settings_refresh_now_summary),
                onClick = {
                    viewModel.refreshAllNow()
                    scope.launch { snackbar.showSnackbar(refreshingMessage) }
                },
            )

            SectionHeader(stringResource(R.string.settings_appearance))
            ValueRow(
                title = stringResource(R.string.settings_theme),
                value = themeLabel(current.themeMode),
                onClick = { dialog = SettingsDialog.Theme },
            )

            SectionHeader(stringResource(R.string.settings_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version)) },
                supportingContent = { Text(BuildConfig.VERSION_NAME) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_directory)) },
                supportingContent = { Text(stringResource(R.string.settings_directory_summary)) },
            )
        }

        when (dialog) {
            SettingsDialog.Speed -> ChoiceDialog(
                title = stringResource(R.string.settings_default_speed),
                options = AppSettings.PlaybackSpeeds,
                selected = current.playbackSpeed,
                label = { formatSpeed(it) },
                onSelect = viewModel::setPlaybackSpeed,
                onDismiss = { dialog = null },
            )
            SettingsDialog.SkipBack -> ChoiceDialog(
                title = stringResource(R.string.settings_skip_back),
                options = AppSettings.SkipBackOptions,
                selected = current.skipBackSeconds,
                label = { secondsLabel(it) },
                onSelect = viewModel::setSkipBack,
                onDismiss = { dialog = null },
            )
            SettingsDialog.SkipForward -> ChoiceDialog(
                title = stringResource(R.string.settings_skip_forward),
                options = AppSettings.SkipForwardOptions,
                selected = current.skipForwardSeconds,
                label = { secondsLabel(it) },
                onSelect = viewModel::setSkipForward,
                onDismiss = { dialog = null },
            )
            SettingsDialog.Theme -> ChoiceDialog(
                title = stringResource(R.string.settings_theme),
                options = ThemeMode.entries,
                selected = current.themeMode,
                label = { themeLabel(it) },
                onSelect = viewModel::setThemeMode,
                onDismiss = { dialog = null },
            )
            null -> Unit
        }
    }
}

@Composable
private fun secondsLabel(seconds: Int) = pluralStringResource(R.plurals.seconds, seconds, seconds)

@Composable
private fun themeLabel(mode: ThemeMode) = stringResource(
    when (mode) {
        ThemeMode.System -> R.string.theme_system
        ThemeMode.Light -> R.string.theme_light
        ThemeMode.Dark -> R.string.theme_dark
    },
)

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    ListItem(
                        headlineContent = { Text(label(option)) },
                        leadingContent = { RadioButton(selected = option == selected, onClick = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = {
                                    onSelect(option)
                                    onDismiss()
                                },
                            ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
