package dev.podwave.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class FormattingTest {

    @Test
    fun `clock labels drop the hour when there is none`() {
        assertThat(formatClock(0)).isEqualTo("0:00")
        assertThat(formatClock(5_000)).isEqualTo("0:05")
        assertThat(formatClock(59_999)).isEqualTo("0:59")
        assertThat(formatClock(65_000)).isEqualTo("1:05")
        assertThat(formatClock(3_723_000)).isEqualTo("1:02:03")
        assertThat(formatClock(36_000_000)).isEqualTo("10:00:00")
        assertThat(formatClock(-5_000)).isEqualTo("0:00")
    }

    @Test
    fun `durations round up to whole minutes`() {
        assertThat(durationParts(0)).isEqualTo(DurationParts(hours = 0, minutes = 1))
        assertThat(durationParts(20_000)).isEqualTo(DurationParts(hours = 0, minutes = 1))
        assertThat(durationParts(2_700_000)).isEqualTo(DurationParts(hours = 0, minutes = 45))
        assertThat(durationParts(3_723_000)).isEqualTo(DurationParts(hours = 1, minutes = 3))
        assertThat(durationParts(7_200_000)).isEqualTo(DurationParts(hours = 2, minutes = 0))
    }

    @Test
    fun `speeds are shown without trailing zeros`() {
        assertThat(formatSpeed(1f)).isEqualTo("1×")
        assertThat(formatSpeed(2f)).isEqualTo("2×")
        assertThat(formatSpeed(0.8f)).isEqualTo("0.8×")
        assertThat(formatSpeed(1.2f)).isEqualTo("1.2×")
        assertThat(formatSpeed(1.5f)).isEqualTo("1.5×")
        assertThat(formatSpeed(1.75f)).isEqualTo("1.75×")
    }

    @Test
    fun `publish dates read naturally`() {
        val now = Instant.parse("2024-10-10T12:00:00Z")
        val utc = ZoneOffset.UTC

        assertThat(publishedLabel(Instant.parse("2024-10-10T01:00:00Z"), now, utc)).isEqualTo(PublishedLabel.Today)
        assertThat(publishedLabel(Instant.parse("2024-10-09T23:00:00Z"), now, utc)).isEqualTo(PublishedLabel.Yesterday)
        assertThat(publishedLabel(Instant.parse("2024-10-07T09:00:00Z"), now, utc)).isEqualTo(PublishedLabel.DaysAgo(3))
        assertThat(publishedLabel(Instant.parse("2024-10-03T09:00:00Z"), now, utc))
            .isEqualTo(PublishedLabel.OnDate(LocalDate.of(2024, 10, 3), showYear = false))
        assertThat(publishedLabel(Instant.parse("2023-12-30T09:00:00Z"), now, utc))
            .isEqualTo(PublishedLabel.OnDate(LocalDate.of(2023, 12, 30), showYear = true))
    }

    @Test
    fun `slightly future timestamps count as today`() {
        val now = Instant.parse("2024-10-10T12:00:00Z")
        assertThat(publishedLabel(Instant.parse("2024-10-11T03:00:00Z"), now, ZoneOffset.UTC)).isEqualTo(PublishedLabel.Today)
    }

    @Test
    fun `days are counted in the listener's time zone`() {
        val now = Instant.parse("2024-10-10T02:00:00Z")
        val published = Instant.parse("2024-10-09T22:00:00Z")

        assertThat(publishedLabel(published, now, ZoneOffset.UTC)).isEqualTo(PublishedLabel.Yesterday)
        // Both instants fall on the evening of October 9 in New York.
        assertThat(publishedLabel(published, now, ZoneId.of("America/New_York"))).isEqualTo(PublishedLabel.Today)
    }
}
