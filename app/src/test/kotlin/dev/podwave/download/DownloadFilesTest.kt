package dev.podwave.download

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DownloadFilesTest {

    @Test
    fun `takes the extension from the path, not the query string`() {
        assertThat(DownloadFiles.extensionFor("https://cdn.example.com/shows/episode.MP3?ad=1&x=y")).isEqualTo("mp3")
        assertThat(DownloadFiles.extensionFor("https://cdn.example.com/episode.m4a#t=30")).isEqualTo("m4a")
        // Tracking prefixes wrap the real URL; the last segment is what gets served.
        assertThat(DownloadFiles.extensionFor("https://track.example.com/r/cdn.example.com/ep.ogg")).isEqualTo("ogg")
    }

    @Test
    fun `falls back to mp3 when the URL has no usable extension`() {
        assertThat(DownloadFiles.extensionFor("https://cdn.example.com/download?id=42")).isEqualTo("mp3")
        assertThat(DownloadFiles.extensionFor("https://cdn.example.com/file.reallylong")).isEqualTo("mp3")
        assertThat(DownloadFiles.extensionFor("https://cdn.example.com/file.m%4a")).isEqualTo("mp3")
    }

    @Test
    fun `names files after the episode id`() {
        assertThat(DownloadFiles.fileName("3fa2c1", "https://cdn.example.com/a.m4a")).isEqualTo("3fa2c1.m4a")
    }

    @Test
    fun `reports whole percentages and copes with unknown sizes`() {
        assertThat(DownloadFiles.progressPercent(copied = 50, total = 200)).isEqualTo(25)
        assertThat(DownloadFiles.progressPercent(copied = 199, total = 200)).isEqualTo(99)
        assertThat(DownloadFiles.progressPercent(copied = 300, total = 200)).isEqualTo(100)
        assertThat(DownloadFiles.progressPercent(copied = 10, total = null)).isEqualTo(0)
        assertThat(DownloadFiles.progressPercent(copied = 10, total = 0)).isEqualTo(0)
    }
}
