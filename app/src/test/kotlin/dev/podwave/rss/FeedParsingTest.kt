package dev.podwave.rss

import com.google.common.truth.Truth.assertThat
import dev.podwave.model.episodeId
import org.junit.Test
import java.time.Instant

class FeedParsingTest {

    @Test
    fun `parses every common duration format`() {
        assertThat(parseDurationMs("1:02:03")).isEqualTo(3_723_000L)
        assertThat(parseDurationMs("62:03")).isEqualTo(3_723_000L)
        assertThat(parseDurationMs("3723")).isEqualTo(3_723_000L)
        assertThat(parseDurationMs(" 45 ")).isEqualTo(45_000L)
        assertThat(parseDurationMs("1:02:03.5")).isEqualTo(3_723_500L)
    }

    @Test
    fun `rejects unusable durations`() {
        assertThat(parseDurationMs(null)).isNull()
        assertThat(parseDurationMs("")).isNull()
        assertThat(parseDurationMs("00:00:00")).isNull()
        assertThat(parseDurationMs("about an hour")).isNull()
        assertThat(parseDurationMs("1:2:3:4")).isNull()
        assertThat(parseDurationMs("-5")).isNull()
    }

    @Test
    fun `parses RFC 822 dates and their real-world variants`() {
        val expected = Instant.parse("2002-10-02T13:00:00Z")
        assertThat(parsePubDate("Wed, 02 Oct 2002 13:00:00 GMT")).isEqualTo(expected)
        assertThat(parsePubDate("Wed, 2 Oct 2002 13:00:00 +0000")).isEqualTo(expected)
        assertThat(parsePubDate("Wed, 2 Oct 2002 08:00:00 EST")).isEqualTo(expected)
        assertThat(parsePubDate("Wed, 2 Oct 2002 06:00:00 PDT")).isEqualTo(expected)
        assertThat(parsePubDate("Wed, 02 Oct 2002 13:00 +0000")).isEqualTo(expected)
        assertThat(parsePubDate("02 Oct 2002 13:00:00 +0000")).isEqualTo(expected)
        assertThat(parsePubDate("Wed,  02   Oct 2002 13:00:00 GMT")).isEqualTo(expected)
        assertThat(parsePubDate("2002-10-02T15:00:00+02:00")).isEqualTo(expected)
    }

    @Test
    fun `recovers from a wrong weekday`() {
        assertThat(parsePubDate("Mon, 02 Oct 2002 13:00:00 +0000")).isEqualTo(Instant.parse("2002-10-02T13:00:00Z"))
    }

    @Test
    fun `gives up on dates it cannot read`() {
        assertThat(parsePubDate("yesterday")).isNull()
        assertThat(parsePubDate(null)).isNull()
    }

    @Test
    fun `interprets explicit flags`() {
        assertThat(parseExplicit("yes")).isTrue()
        assertThat(parseExplicit("True")).isTrue()
        assertThat(parseExplicit("explicit")).isTrue()
        assertThat(parseExplicit("clean")).isFalse()
        assertThat(parseExplicit("no")).isFalse()
        assertThat(parseExplicit(null)).isFalse()
    }

    @Test
    fun `episode ids are stable and scoped to the feed`() {
        val first = episodeId("https://a.example/feed", "ep-1")
        assertThat(first).isEqualTo(episodeId("https://a.example/feed", "ep-1"))
        assertThat(first).isNotEqualTo(episodeId("https://b.example/feed", "ep-1"))
        assertThat(first).hasLength(24)
        assertThat(first).matches("[0-9a-f]+")
    }
}
