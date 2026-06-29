package dev.podwave.ui.episode

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimestampsTest {

    @Test
    fun `finds minute and hour timestamps`() {
        val text = "Intro 0:00, guest at 12:34 and outro at 1:02:03."

        val matches = Timestamps.find(text)

        assertThat(matches.map { it.positionMs }).containsExactly(0L, 754_000L, 3_723_000L).inOrder()
        assertThat(matches.map { text.substring(it.range) }).containsExactly("0:00", "12:34", "1:02:03").inOrder()
    }

    @Test
    fun `drops timestamps past the end of the episode`() {
        val matches = Timestamps.find("Recap at 05:00, bonus at 1:30:00", maxMs = 3_600_000)
        assertThat(matches.map { it.positionMs }).containsExactly(300_000L)
    }

    @Test
    fun `ignores things that only look like timestamps`() {
        assertThat(Timestamps.find("Recorded 2024:10:02")).isEmpty()
        assertThat(Timestamps.find("Score was 10:65")).isEmpty()
        assertThat(Timestamps.find("Call 555-1234 or try 3:4")).isEmpty()
    }
}
