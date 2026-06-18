package dev.podwave.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class SleepTimerTest {

    @Test
    fun `starting a countdown measures from now`() {
        assertThat(SleepTimer.start(SleepTimerOption.Minutes(15), now = 1_000))
            .isEqualTo(SleepTimerState.Countdown(endsAt = 901_000))
        assertThat(SleepTimer.start(SleepTimerOption.EndOfEpisode, now = 1_000)).isEqualTo(SleepTimerState.EndOfEpisode)
        assertThat(SleepTimer.start(SleepTimerOption.Off, now = 1_000)).isEqualTo(SleepTimerState.Off)
    }

    @Test
    fun `a countdown needs a positive length`() {
        assertThrows(IllegalArgumentException::class.java) { SleepTimer.start(SleepTimerOption.Minutes(0), now = 0) }
    }

    @Test
    fun `remaining time never goes negative and only exists for countdowns`() {
        val timer = SleepTimerState.Countdown(endsAt = 10_000)
        assertThat(SleepTimer.remainingMs(timer, now = 4_000)).isEqualTo(6_000)
        assertThat(SleepTimer.remainingMs(timer, now = 12_000)).isEqualTo(0)
        assertThat(SleepTimer.remainingMs(SleepTimerState.Off, now = 0)).isNull()
        assertThat(SleepTimer.remainingMs(SleepTimerState.EndOfEpisode, now = 0)).isNull()
    }

    @Test
    fun `expires exactly at the deadline`() {
        val timer = SleepTimerState.Countdown(endsAt = 10_000)
        assertThat(SleepTimer.isExpired(timer, now = 9_999)).isFalse()
        assertThat(SleepTimer.isExpired(timer, now = 10_000)).isTrue()
        assertThat(SleepTimer.isExpired(SleepTimerState.EndOfEpisode, now = Long.MAX_VALUE)).isFalse()
    }

    @Test
    fun `fades out over the last few seconds`() {
        val timer = SleepTimerState.Countdown(endsAt = 100_000)
        assertThat(SleepTimer.volumeFor(timer, now = 50_000)).isEqualTo(1f)
        assertThat(SleepTimer.volumeFor(timer, now = 90_000)).isEqualTo(1f)
        assertThat(SleepTimer.volumeFor(timer, now = 95_000)).isWithin(0.001f).of(0.5f)
        assertThat(SleepTimer.volumeFor(timer, now = 100_000)).isEqualTo(0f)
        assertThat(SleepTimer.volumeFor(SleepTimerState.Off, now = 0)).isEqualTo(1f)
    }

    @Test
    fun `extending adds to the deadline, or to now if it already passed`() {
        val running = SleepTimerState.Countdown(endsAt = 100_000)
        assertThat(SleepTimer.extend(running, minutes = 5, now = 40_000))
            .isEqualTo(SleepTimerState.Countdown(endsAt = 400_000))
        assertThat(SleepTimer.extend(running, minutes = 5, now = 200_000))
            .isEqualTo(SleepTimerState.Countdown(endsAt = 500_000))
    }

    @Test
    fun `extending a stopped timer starts a countdown`() {
        assertThat(SleepTimer.extend(SleepTimerState.Off, minutes = 5, now = 1_000))
            .isEqualTo(SleepTimerState.Countdown(endsAt = 301_000))
        assertThat(SleepTimer.extend(SleepTimerState.EndOfEpisode, minutes = 5, now = 1_000))
            .isEqualTo(SleepTimerState.Countdown(endsAt = 301_000))
    }
}
