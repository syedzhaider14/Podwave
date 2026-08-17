package app.podwave.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SleepTimerState {
    data object Off : SleepTimerState

    data class Countdown(val endsAtMs: Long) : SleepTimerState

    data object EndOfEpisode : SleepTimerState
}

/**
 * Sleep timer state machine. Time is injected so it can be tested without
 * waiting; the player polls [shouldPauseNow] while playing.
 */
class SleepTimer(private val now: () -> Long) {
    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    fun start(minutes: Int) {
        require(minutes > 0) { "minutes must be positive" }
        _state.value = SleepTimerState.Countdown(now() + minutes * 60_000L)
    }

    /** Adds time to a running countdown, e.g. a "+5 min" button. */
    fun extend(minutes: Int) {
        val current = _state.value
        if (current is SleepTimerState.Countdown) {
            _state.value = SleepTimerState.Countdown(maxOf(current.endsAtMs, now()) + minutes * 60_000L)
        }
    }

    fun stopAtEndOfEpisode() {
        _state.value = SleepTimerState.EndOfEpisode
    }

    fun cancel() {
        _state.value = SleepTimerState.Off
    }

    fun remainingMs(): Long? = (_state.value as? SleepTimerState.Countdown)?.let { (it.endsAtMs - now()).coerceAtLeast(0) }

    /** True once a countdown has run out. Resets the timer so it fires only once. */
    fun shouldPauseNow(): Boolean {
        val current = _state.value
        if (current is SleepTimerState.Countdown && now() >= current.endsAtMs) {
            _state.value = SleepTimerState.Off
            return true
        }
        return false
    }

    /** Called when an episode finishes. Returns true if playback should stop instead of continuing. */
    fun onEpisodeEnded(): Boolean {
        if (_state.value == SleepTimerState.EndOfEpisode) {
            _state.value = SleepTimerState.Off
            return true
        }
        return false
    }
}
