package app.podwave

import app.podwave.domain.SleepTimer
import app.podwave.domain.SleepTimerState
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class SleepTimerTest {
    private var now = 1_000_000L
    private val timer = SleepTimer { now }

    @Test
    fun `countdown pauses once when time is up`() {
        timer.start(minutes = 15)
        assertThat(timer.remainingMs()).isEqualTo(15 * 60_000L)

        now += 14 * 60_000L
        assertThat(timer.shouldPauseNow()).isFalse()

        now += 60_000L
        assertThat(timer.shouldPauseNow()).isTrue()
        assertThat(timer.state.value).isEqualTo(SleepTimerState.Off)
        assertThat(timer.shouldPauseNow()).isFalse()
    }

    @Test
    fun `extend adds time from now if the timer already ran out`() {
        timer.start(minutes = 5)
        timer.extend(minutes = 5)
        assertThat(timer.remainingMs()).isEqualTo(10 * 60_000L)

        now += 20 * 60_000L
        timer.extend(minutes = 1)
        assertThat(timer.remainingMs()).isEqualTo(60_000L)
    }

    @Test
    fun `end of episode stops at the next episode boundary`() {
        timer.stopAtEndOfEpisode()
        assertThat(timer.shouldPauseNow()).isFalse()
        assertThat(timer.onEpisodeEnded()).isTrue()
        assertThat(timer.onEpisodeEnded()).isFalse()
    }

    @Test
    fun `cancel turns it off and countdowns ignore episode ends`() {
        timer.start(minutes = 30)
        assertThat(timer.onEpisodeEnded()).isFalse()
        timer.cancel()
        assertThat(timer.state.value).isEqualTo(SleepTimerState.Off)
        assertThat(timer.remainingMs()).isNull()
    }

    @Test
    fun `rejects non-positive durations`() {
        assertThrows(IllegalArgumentException::class.java) { timer.start(0) }
    }
}
