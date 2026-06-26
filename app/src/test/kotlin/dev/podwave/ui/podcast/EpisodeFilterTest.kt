package dev.podwave.ui.podcast

import com.google.common.truth.Truth.assertThat
import dev.podwave.model.DownloadStatus
import dev.podwave.testing.testEpisode
import org.junit.Test
import java.time.Instant

class EpisodeFilterTest {
    private val played = testEpisode(id = "played", playedAt = Instant.EPOCH)
    private val downloaded = testEpisode(id = "downloaded", downloadStatus = DownloadStatus.Downloaded)
    private val downloading = testEpisode(id = "downloading", downloadStatus = DownloadStatus.Downloading)
    private val episodes = listOf(played, downloaded, downloading)

    @Test
    fun `all keeps everything in order`() {
        assertThat(EpisodeFilter.All.apply(episodes)).containsExactlyElementsIn(episodes).inOrder()
    }

    @Test
    fun `unplayed hides finished episodes`() {
        assertThat(EpisodeFilter.Unplayed.apply(episodes)).containsExactly(downloaded, downloading).inOrder()
    }

    @Test
    fun `downloaded only shows finished downloads`() {
        assertThat(EpisodeFilter.Downloaded.apply(episodes)).containsExactly(downloaded)
    }
}
