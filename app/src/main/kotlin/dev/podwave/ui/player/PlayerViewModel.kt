package dev.podwave.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.podwave.data.EpisodeRepository
import dev.podwave.data.SettingsRepository
import dev.podwave.model.AppSettings
import dev.podwave.model.Episode
import dev.podwave.playback.PlayerController
import dev.podwave.playback.PlayerState
import dev.podwave.playback.SleepTimerOption
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SkipIntervals(
    val backSeconds: Int = AppSettings().skipBackSeconds,
    val forwardSeconds: Int = AppSettings().skipForwardSeconds,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val player: PlayerController,
    settings: SettingsRepository,
    episodes: EpisodeRepository,
) : ViewModel() {

    val state: StateFlow<PlayerState> = player.state

    val skipIntervals: StateFlow<SkipIntervals> = settings.settings
        .map { SkipIntervals(it.skipBackSeconds, it.skipForwardSeconds) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SkipIntervals())

    /** The next queued episode, shown under the controls. */
    val upNext: StateFlow<Episode?> = episodes.observeQueue()
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun togglePlayPause() = launch { player.togglePlayPause() }

    fun seekTo(positionMs: Long) = launch { player.seekTo(positionMs) }

    fun skipBack() = launch { player.skipBack() }

    fun skipForward() = launch { player.skipForward() }

    fun skipToNext() = launch { player.skipToNext() }

    fun setSpeed(speed: Float) = launch { player.setSpeed(speed) }

    fun setSleepTimer(option: SleepTimerOption) = launch { player.setSleepTimer(option) }

    fun extendSleepTimer(minutes: Int) = launch { player.extendSleepTimer(minutes) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
