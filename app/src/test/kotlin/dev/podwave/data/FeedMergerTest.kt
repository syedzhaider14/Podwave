package dev.podwave.data

import com.google.common.truth.Truth.assertThat
import dev.podwave.model.DownloadStatus
import dev.podwave.model.episodeId
import dev.podwave.testing.FEED_URL
import dev.podwave.testing.episodeEntity
import dev.podwave.testing.parsedEpisode
import org.junit.Test
import java.time.Instant

class FeedMergerTest {
    private val now = Instant.parse("2024-10-10T08:00:00Z")

    @Test
    fun `everything is new on the first fetch, newest first`() {
        val parsed = listOf(
            parsedEpisode("old", publishedAt = Instant.parse("2024-09-01T00:00:00Z")),
            parsedEpisode("newest", publishedAt = Instant.parse("2024-10-01T00:00:00Z")),
            parsedEpisode("middle", publishedAt = Instant.parse("2024-09-15T00:00:00Z")),
        )

        val result = FeedMerger.merge(FEED_URL, parsed, existing = emptyList(), now = now)

        assertThat(result.episodes).hasSize(3)
        assertThat(result.episodes.map { it.addedAt }.distinct()).containsExactly(now)
        assertThat(result.newEpisodeIds)
            .containsExactly(episodeId(FEED_URL, "newest"), episodeId(FEED_URL, "middle"), episodeId(FEED_URL, "old"))
            .inOrder()
    }

    @Test
    fun `feed metadata wins but listener state is kept`() {
        val id = episodeId(FEED_URL, "a")
        val playedAt = Instant.parse("2024-10-05T20:00:00Z")
        val stored = episodeEntity(
            id = id,
            guid = "a",
            title = "Old title with a typo",
            positionMs = 120_000,
            playedAt = playedAt,
            downloadStatus = DownloadStatus.Downloaded,
            localFilePath = "/data/episodes/$id.mp3",
        )
        val parsed = parsedEpisode("a", title = "Fixed title", audioUrl = "https://new-host.example.com/a.mp3")

        val merged = FeedMerger.merge(FEED_URL, listOf(parsed), existing = listOf(stored), now = now).episodes.single()

        assertThat(merged.title).isEqualTo("Fixed title")
        assertThat(merged.audioUrl).isEqualTo("https://new-host.example.com/a.mp3")
        assertThat(merged.positionMs).isEqualTo(120_000)
        assertThat(merged.playedAt).isEqualTo(playedAt)
        assertThat(merged.downloadStatus).isEqualTo(DownloadStatus.Downloaded)
        assertThat(merged.localFilePath).isEqualTo("/data/episodes/$id.mp3")
        assertThat(merged.addedAt).isEqualTo(stored.addedAt)
    }

    @Test
    fun `keeps a known duration and date when a later fetch omits them`() {
        val id = episodeId(FEED_URL, "a")
        val published = Instant.parse("2024-09-30T10:00:00Z")
        val stored = episodeEntity(id = id, guid = "a", durationMs = 1_234_000, publishedAt = published)

        val merged = FeedMerger.merge(
            FEED_URL,
            listOf(parsedEpisode("a", durationMs = null, publishedAt = null)),
            existing = listOf(stored),
            now = now,
        ).episodes.single()

        assertThat(merged.durationMs).isEqualTo(1_234_000)
        assertThat(merged.publishedAt).isEqualTo(published)
    }

    @Test
    fun `only unseen guids count as new`() {
        val stored = episodeEntity(id = episodeId(FEED_URL, "a"), guid = "a")

        val result = FeedMerger.merge(FEED_URL, listOf(parsedEpisode("b"), parsedEpisode("a")), listOf(stored), now)

        assertThat(result.newEpisodeIds).containsExactly(episodeId(FEED_URL, "b"))
    }

    @Test
    fun `duplicate guids in a feed collapse to the first item`() {
        val result = FeedMerger.merge(
            FEED_URL,
            listOf(parsedEpisode("a", title = "First"), parsedEpisode("a", title = "Repost")),
            existing = emptyList(),
            now = now,
        )

        assertThat(result.episodes.map { it.title }).containsExactly("First")
    }

    @Test
    fun `subscribing to a back catalogue only announces the latest episode`() {
        val result = MergeResult(episodes = emptyList(), newEpisodeIds = listOf("3", "2", "1"))

        assertThat(FeedMerger.newEpisodesToAnnounce(result, isFirstFetch = true)).containsExactly("3")
        assertThat(FeedMerger.newEpisodesToAnnounce(result, isFirstFetch = false)).containsExactly("3", "2", "1").inOrder()
    }
}
