package app.podwave.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podwave.data.repository.PodcastRepository
import app.podwave.data.settings.Settings
import app.podwave.data.settings.SettingsRepository
import app.podwave.rss.Opml
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.Reader

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val podcasts: PodcastRepository,
    private val opml: Opml,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    fun setWifiOnly(value: Boolean) {
        viewModelScope.launch { settingsRepository.setDownloadOnWifiOnly(value) }
    }

    fun setAutoRefresh(value: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoRefresh(value) }
    }

    /** Produces the OPML document; the screen writes it to the file the user picked. */
    suspend fun exportOpml(): String = podcasts.exportOpml()

    fun importOpml(openReader: () -> Reader?) {
        viewModelScope.launch {
            _importing.value = true
            try {
                val outlines = openReader()?.use { opml.parse(it) }.orEmpty()
                if (outlines.isEmpty()) {
                    messageChannel.send("No podcasts found in that file.")
                } else {
                    val summary = podcasts.import(outlines)
                    messageChannel.send(
                        buildString {
                            append("Added ${summary.added}")
                            if (summary.alreadySubscribed > 0) append(", ${summary.alreadySubscribed} already subscribed")
                            if (summary.failed.isNotEmpty()) append(", ${summary.failed.size} failed")
                        },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messageChannel.send("Couldn't read that file: ${e.message}")
            } finally {
                _importing.value = false
            }
        }
    }

    fun reportExported(success: Boolean) {
        viewModelScope.launch { messageChannel.send(if (success) "Subscriptions exported" else "Export failed") }
    }
}
