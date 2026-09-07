package app.podwave

import app.podwave.data.db.EpisodeEntity
import app.podwave.data.repository.FeedMerger
import app.podwave.domain.DownloadState
import app.podwave.rss.EpisodeType
import app.podwave.rss.ParsedEpisode
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class FeedMergerTest {
    private fun parsed(guid: String, title: String = guid, duration: Long? = 600) = ParsedEpisode(
        guid = guid,
        title = title,
        description = null,
        link = null,
        audioUrl = "https://cdn.example.com/$guid.mp3",
        audioType = "audio/mpeg",
        audioLengthBytes = null,
        durationSeconds = duration,
        publishedAt = Instant.parse("2025-01-01T00:00:00Z"),
        artworkUrl = null,
        season = null,
        episode = null,
        type = EpisodeType.Full,
        explicit = false,
    )

    private fun stored(guid: String, title: String = guid) = EpisodeEntity(
        id = guid.hashCode().toLong(),
        podcastId = 7,
        guid = guid,
        title = title,
        description = null,
        audioUrl = "https://cdn.example.com/$guid.mp3",
        audioType = "audio/mpeg",
        durationSeconds = 600,
        publishedAt = Instant.parse("2025-01-01T00:00:00Z").toEpochMilli(),
        artworkUrl = null,
        season = null,
        episodeNumber = null,
        type = EpisodeType.Full,
        playbackPositionMs = 120_000,
        completed = false,
        downloadState = DownloadState.Downloaded,
        localFilePath = "/files/$guid.mp3",
        discoveredAt = 1,
    )

    @Test
    fun `new guids are inserted, unchanged ones are left alone`() {
        val result = FeedMerger.merge(
            podcastId = 7,
            existing = listOf(stored("a")),
            parsed = listOf(parsed("a"), parsed("b")),
            now = 99,
        )
        assertThat(result.toInsert.map { it.guid }).containsExactly("b")
        assertThat(result.toInsert.single().discoveredAt).isEqualTo(99)
        assertThat(result.toInsert.single().podcastId).isEqualTo(7)
        assertThat(result.toUpdate).isEmpty()
    }

    @Test
    fun `metadata changes update the episode but keep listening state and downloads`() {
        val result = FeedMerger.merge(
            podcastId = 7,
            existing = listOf(stored("a", title = "Old title")),
            parsed = listOf(parsed("a", title = "Fixed title")),
            now = 99,
        )
        val updated = result.toUpdate.single()
        assertThat(updated.title).isEqualTo("Fixed title")
        assertThat(updated.playbackPositionMs).isEqualTo(120_000)
        assertThat(updated.downloadState).isEqualTo(DownloadState.Downloaded)
        assertThat(updated.localFilePath).isEqualTo("/files/a.mp3")
    }

    @Test
    fun `a missing duration in the feed doesn't erase a known one`() {
        val result = FeedMerger.merge(7, listOf(stored("a")), listOf(parsed("a", duration = null)), now = 99)
        assertThat(result.toUpdate).isEmpty()
    }

    @Test
    fun `episodes removed from the feed are kept`() {
        val result = FeedMerger.merge(7, listOf(stored("gone")), emptyList(), now = 99)
        assertThat(result.toInsert).isEmpty()
        assertThat(result.toUpdate).isEmpty()
    }
}
