package app.podwave.rss

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class Rfc822DatesTest {
    private fun parse(value: String) = Rfc822Dates.parse(value)

    @Test
    fun `parses standard RFC 822 dates`() {
        assertThat(parse("Tue, 10 Jun 2025 04:00:00 +0000")).isEqualTo(Instant.parse("2025-06-10T04:00:00Z"))
        assertThat(parse("Tue, 10 Jun 2025 04:00:00 GMT")).isEqualTo(Instant.parse("2025-06-10T04:00:00Z"))
        assertThat(parse("10 Jun 2025 04:00:00 -0400")).isEqualTo(Instant.parse("2025-06-10T08:00:00Z"))
        assertThat(parse("Tue, 3 Jun 2025 04:00:00 +0000")).isEqualTo(Instant.parse("2025-06-03T04:00:00Z"))
    }

    @Test
    fun `tolerates real-world deviations`() {
        // Named US time zone
        assertThat(parse("Mon, 02 Jun 2025 09:30:00 PDT")).isEqualTo(Instant.parse("2025-06-02T16:30:00Z"))
        // Wrong weekday (10 June 2025 was a Tuesday)
        assertThat(parse("Fri, 10 Jun 2025 04:00:00 +0000")).isEqualTo(Instant.parse("2025-06-10T04:00:00Z"))
        // Missing seconds
        assertThat(parse("Tue, 10 Jun 2025 04:00 +0000")).isEqualTo(Instant.parse("2025-06-10T04:00:00Z"))
        // ISO 8601
        assertThat(parse("2025-06-10T04:00:00Z")).isEqualTo(Instant.parse("2025-06-10T04:00:00Z"))
        assertThat(parse("2025-06-10T06:00:00+02:00")).isEqualTo(Instant.parse("2025-06-10T04:00:00Z"))
        // Extra whitespace
        assertThat(parse("  Tue,  10 Jun 2025   04:00:00 +0000 ")).isEqualTo(Instant.parse("2025-06-10T04:00:00Z"))
    }

    @Test
    fun `returns null when nothing matches`() {
        assertThat(parse("")).isNull()
        assertThat(parse("last tuesday")).isNull()
        assertThat(Rfc822Dates.parse(null)).isNull()
    }
}
