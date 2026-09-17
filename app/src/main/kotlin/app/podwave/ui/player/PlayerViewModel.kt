package app.podwave.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podwave.data.settings.SettingsRepository
import app.podwave.domain.SleepTimerState
import app.podwave.playback.PlayerController
import app.podwave.playback.PlayerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val player: PlayerController,
) : ViewModel() {

    val state: StateFlow<PlayerState> = player.state
    val sleepTimer: StateFlow<SleepTimerState> = player.sleepTimer.state

    /** Remaining sleep-timer time, ticking once a second while a countdown runs. */
    val sleepRemainingMs: Flow<Long?> = flow {
        while (true) {
            emit(player.sleepTimer.remainingMs())
            delay(1_000)
        }
    }

    val speeds: List<Float> = SettingsRepository.SPEEDS

    init {
        // Make sure controls work even if the app was opened from the notification.
        viewModelScope.launch { player.connect() }
    }

    fun togglePlayPause() = player.togglePlayPause()

    fun seekBack() = player.seekBack()

    fun seekForward() = player.seekForward()

    fun seekTo(fraction: Float) {
        val duration = state.value.durationMs
        if (duration > 0) player.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun setSpeed(speed: Float) = player.setSpeed(speed)

    fun skipToNext() = player.skipToNext()

    fun startSleepTimer(minutes: Int) = player.sleepTimer.start(minutes)

    fun sleepAtEndOfEpisode() = player.sleepTimer.stopAtEndOfEpisode()

    fun cancelSleepTimer() = player.sleepTimer.cancel()
}
