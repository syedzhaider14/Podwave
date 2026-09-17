package app.podwave.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    appVersion: String,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importOpml { context.contentResolver.openInputStream(uri)?.reader() }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = runCatching {
                    val xml = viewModel.exportOpml()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(xml.toByteArray()) }
                    } != null
                }.getOrDefault(false)
                viewModel.reportExported(ok)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text("Download on Wi-Fi only") },
                supportingContent = { Text("Avoid using mobile data for episode downloads") },
                trailingContent = { Switch(checked = settings.downloadOnWifiOnly, onCheckedChange = viewModel::setWifiOnly) },
            )
            ListItem(
                headlineContent = { Text("Check for new episodes automatically") },
                supportingContent = { Text("Every few hours, when the battery isn't low") },
                trailingContent = { Switch(checked = settings.autoRefresh, onCheckedChange = viewModel::setAutoRefresh) },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                "Subscriptions",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text("Import from OPML") },
                supportingContent = { Text("Bring your podcasts from another app") },
                trailingContent = { if (importing) CircularProgressIndicator(Modifier.size(24.dp)) },
                modifier = Modifier.clickable(enabled = !importing) {
                    importLauncher.launch(arrayOf("text/xml", "text/x-opml", "application/xml", "application/octet-stream"))
                },
            )
            ListItem(
                headlineContent = { Text("Export to OPML") },
                supportingContent = { Text("Back up your subscriptions or move them elsewhere") },
                modifier = Modifier.clickable { exportLauncher.launch("podwave-subscriptions.opml") },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ListItem(
                headlineContent = { Text("Podcast search") },
                supportingContent = { Text("Powered by the iTunes Search API") },
            )
            ListItem(headlineContent = { Text("Version") }, supportingContent = { Text(appVersion) })
        }
    }
}
