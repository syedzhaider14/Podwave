package dev.podwave.playback

sealed interface SleepTimerState {
    data object Off : SleepTimerState

    /** Pause when the current episode finishes instead of moving on to the next one. */
    data object EndOfEpisode : SleepTimerState

    /** [endsAt] is on the monotonic clock (`SystemClock.elapsedRealtime`), so it ignores clock changes. */
    data class Countdown(val endsAt: Long) : SleepTimerState
}

sealed interface SleepTimerOption {
    data object Off : SleepTimerOption

    data object EndOfEpisode : SleepTimerOption

    data class Minutes(val minutes: Int) : SleepTimerOption

    companion object {
        val Presets = listOf(Minutes(5), Minutes(15), Minutes(30), Minutes(45), Minutes(60), EndOfEpisode)
    }
}

object SleepTimer {
    fun start(option: SleepTimerOption, now: Long): SleepTimerState = when (option) {
        SleepTimerOption.Off -> SleepTimerState.Off
        SleepTimerOption.EndOfEpisode -> SleepTimerState.EndOfEpisode
        is SleepTimerOption.Minutes -> {
            require(option.minutes > 0) { "Sleep timer needs a positive duration" }
            SleepTimerState.Countdown(endsAt = now + option.minutes * 60_000L)
        }
    }

    /** Milliseconds left on a countdown, or null when no countdown is running. */
    fun remainingMs(state: SleepTimerState, now: Long): Long? =
        (state as? SleepTimerState.Countdown)?.let { (it.endsAt - now).coerceAtLeast(0) }

    fun isExpired(state: SleepTimerState, now: Long): Boolean =
        state is SleepTimerState.Countdown && now >= state.endsAt

    /**
     * Volume multiplier for the last stretch of a countdown, so playback fades out instead of
     * cutting off mid-sentence.
     */
    fun volumeFor(state: SleepTimerState, now: Long, fadeMs: Long = FADE_MS): Float {
        val remaining = remainingMs(state, now) ?: return 1f
        return if (remaining >= fadeMs) 1f else (remaining.toFloat() / fadeMs).coerceIn(0f, 1f)
    }

    /** Adds time to a running countdown ("+5 min"), or starts one if none is running. */
    fun extend(state: SleepTimerState, minutes: Int, now: Long): SleepTimerState = when (state) {
        is SleepTimerState.Countdown -> state.copy(endsAt = maxOf(state.endsAt, now) + minutes * 60_000L)
        else -> start(SleepTimerOption.Minutes(minutes), now)
    }

    const val FADE_MS = 10_000L
}
