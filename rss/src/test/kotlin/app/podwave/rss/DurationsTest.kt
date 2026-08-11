package app.podwave.rss

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DurationsTest {
    @Test
    fun `parses every common itunes duration format`() {
        assertThat(parseItunesDuration("3723")).isEqualTo(3723L)
        assertThat(parseItunesDuration("3723.9")).isEqualTo(3723L)
        assertThat(parseItunesDuration("62:03")).isEqualTo(3723L)
        assertThat(parseItunesDuration("1:02:03")).isEqualTo(3723L)
        assertThat(parseItunesDuration(" 00:45 ")).isEqualTo(45L)
        assertThat(parseItunesDuration("0")).isEqualTo(0L)
    }

    @Test
    fun `rejects garbage`() {
        assertThat(parseItunesDuration(null)).isNull()
        assertThat(parseItunesDuration("")).isNull()
        assertThat(parseItunesDuration("about an hour")).isNull()
        assertThat(parseItunesDuration("1::2")).isNull()
        assertThat(parseItunesDuration("1:2:3:4")).isNull()
        assertThat(parseItunesDuration("-5")).isNull()
    }

    @Test
    fun `formats durations`() {
        assertThat(formatDuration(3723)).isEqualTo("1:02:03")
        assertThat(formatDuration(125)).isEqualTo("2:05")
        assertThat(formatDuration(0)).isEqualTo("0:00")
        assertThat(formatDuration(-10)).isEqualTo("0:00")
    }
}
