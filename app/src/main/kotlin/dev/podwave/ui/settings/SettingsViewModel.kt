package dev.podwave.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.podwave.data.SettingsRepository
import dev.podwave.download.FeedRefreshWorker
import dev.podwave.model.AppSettings
import dev.podwave.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<AppSettings?> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setPlaybackSpeed(speed: Float) = update { settings.setPlaybackSpeed(speed) }

    fun setSkipBack(seconds: Int) = update { settings.setSkipBack(seconds) }

    fun setSkipForward(seconds: Int) = update { settings.setSkipForward(seconds) }

    fun setAutoDownload(enabled: Boolean) = update { settings.setAutoDownload(enabled) }

    fun setWifiOnly(enabled: Boolean) = update { settings.setWifiOnly(enabled) }

    fun setThemeMode(mode: ThemeMode) = update { settings.setThemeMode(mode) }

    fun refreshAllNow() = FeedRefreshWorker.refreshNow(context)

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
