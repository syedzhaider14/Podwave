package dev.podwave.playback

import com.google.common.truth.Truth.assertThat
import dev.podwave.testing.testEpisode
import org.junit.Test
import java.time.Instant

class StartPositionTest {

    @Test
    fun `resumes where the listener left off`() {
        assertThat(testEpisode(positionMs = 600_000, durationMs = 3_600_000).startPositionMs()).isEqualTo(600_000)
    }

    @Test
    fun `starts over when the episode is played or nearly finished`() {
        assertThat(testEpisode(positionMs = 600_000, playedAt = Instant.EPOCH).startPositionMs()).isEqualTo(0)
        assertThat(testEpisode(positionMs = 3_580_000, durationMs = 3_600_000).startPositionMs()).isEqualTo(0)
    }

    @Test
    fun `resumes when the length is unknown`() {
        assertThat(testEpisode(positionMs = 42_000, durationMs = null).startPositionMs()).isEqualTo(42_000)
    }
}
