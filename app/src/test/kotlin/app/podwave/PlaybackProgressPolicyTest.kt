package app.podwave

import app.podwave.domain.PlaybackProgressPolicy
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackProgressPolicyTest {
    private val hour = 3_600_000L

    @Test
    fun `episodes are played at 95 percent`() {
        assertThat(PlaybackProgressPolicy.isCompleted((hour * 0.94).toLong(), hour)).isFalse()
        assertThat(PlaybackProgressPolicy.isCompleted((hour * 0.95).toLong(), hour)).isTrue()
    }

    @Test
    fun `the last 30 seconds count as played even before 95 percent`() {
        // In a 5-minute episode, 30 seconds is 10%, so this rule is what kicks in.
        val fiveMinutes = 300_000L
        assertThat(PlaybackProgressPolicy.isCompleted(fiveMinutes - 29_000, fiveMinutes)).isTrue()
        assertThat(PlaybackProgressPolicy.isCompleted(fiveMinutes - 50_000, fiveMinutes)).isFalse()
    }

    @Test
    fun `short clips aren't completed just by starting them`() {
        assertThat(PlaybackProgressPolicy.isCompleted(5_000, 40_000)).isFalse()
        assertThat(PlaybackProgressPolicy.isCompleted(0, 40_000)).isFalse()
        assertThat(PlaybackProgressPolicy.isCompleted(10_000, 0)).isFalse()
    }

    @Test
    fun `resume rewinds a little and ignores tiny or finished positions`() {
        assertThat(PlaybackProgressPolicy.resumePosition(600_000, hour, completed = false)).isEqualTo(597_000)
        assertThat(PlaybackProgressPolicy.resumePosition(4_000, hour, completed = false)).isEqualTo(0)
        assertThat(PlaybackProgressPolicy.resumePosition(600_000, hour, completed = true)).isEqualTo(0)
        assertThat(PlaybackProgressPolicy.resumePosition(hour - 10_000, hour, completed = false)).isEqualTo(0)
        assertThat(PlaybackProgressPolicy.resumePosition(600_000, null, completed = false)).isEqualTo(597_000)
    }
}
