package dev.podwave.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.podwave.model.DownloadStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PodwaveDatabaseTest {
    private lateinit var db: PodwaveDatabase
    private val podcasts get() = db.podcastDao()
    private val episodes get() = db.episodeDao()
    private val queue get() = db.queueDao()

    private val feedUrl = "https://feeds.example.com/signal"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PodwaveDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed(vararg ids: String, subscribed: Boolean = true) {
        podcasts.upsert(
            PodcastEntity(
                feedUrl = feedUrl,
                title = "Signal & Noise",
                author = "Example Media",
                description = "",
                artworkUrl = "https://cdn.example.com/show.jpg",
                websiteUrl = null,
                categories = listOf("Technology", "Science"),
                subscribed = subscribed,
                subscribedAt = null,
                lastRefreshedAt = null,
                etag = null,
            ),
        )
        episodes.upsertAll(ids.mapIndexed { index, id -> episode(id, publishedAt = Instant.ofEpochSecond(index * 1_000L)) })
    }

    private fun episode(id: String, publishedAt: Instant? = null, title: String = "Episode $id") = EpisodeEntity(
        id = id,
        podcastFeedUrl = feedUrl,
        guid = id,
        title = title,
        description = "",
        audioUrl = "https://cdn.example.com/$id.mp3",
        audioMimeType = "audio/mpeg",
        audioSizeBytes = null,
        durationMs = 1_800_000,
        publishedAt = publishedAt,
        artworkUrl = null,
        season = null,
        episodeNumber = null,
        explicit = false,
        addedAt = Instant.EPOCH,
    )

    @Test
    fun queueKeepsInsertionOrderAndMovesReaddedItems() = runTest {
        seed("a", "b", "c")

        queue.addLast("a")
        queue.addLast("b")
        queue.addFirst("c")
        assertThat(queue.getIds()).containsExactly("c", "a", "b").inOrder()

        queue.addLast("c")
        assertThat(queue.getIds()).containsExactly("a", "b", "c").inOrder()

        queue.replaceOrder(listOf("b", "c", "a"))
        assertThat(queue.observe().first().map { it.episode.id }).containsExactly("b", "c", "a").inOrder()
    }

    @Test
    fun refreshingEpisodesDoesNotEmptyTheQueue() = runTest {
        // REPLACE would delete and reinsert the rows, cascading the delete into the queue.
        seed("a", "b")
        queue.addLast("a")

        episodes.upsertAll(listOf(episode("a", title = "Renamed"), episode("b")))

        assertThat(queue.getIds()).containsExactly("a")
        assertThat(episodes.get("a")?.episode?.title).isEqualTo("Renamed")
    }

    @Test
    fun episodesComeWithTheirShowsNameAndArtwork() = runTest {
        seed("a")

        val loaded = episodes.get("a")!!.asModel()

        assertThat(loaded.podcastTitle).isEqualTo("Signal & Noise")
        assertThat(loaded.artworkUrl).isEqualTo("https://cdn.example.com/show.jpg")
        assertThat(podcasts.get(feedUrl)?.categories).containsExactly("Technology", "Science").inOrder()
    }

    @Test
    fun continueListeningOnlyHasUnfinishedEpisodes() = runTest {
        seed("fresh", "started", "finished")
        episodes.updatePosition("started", 60_000, Instant.ofEpochSecond(10))
        episodes.updatePosition("finished", 60_000, Instant.ofEpochSecond(20))
        episodes.markPlayed("finished", Instant.ofEpochSecond(30))

        val inProgress = episodes.observeInProgress(limit = 10).first().map { it.episode.id }

        assertThat(inProgress).containsExactly("started")
    }

    @Test
    fun latestOnlyListsSubscribedShows() = runTest {
        seed("a", "b", subscribed = false)
        assertThat(episodes.observeLatestUnplayed(limit = 10).first()).isEmpty()

        podcasts.setSubscribed(feedUrl, subscribed = true, at = Instant.EPOCH)
        assertThat(episodes.observeLatestUnplayed(limit = 10).first().map { it.episode.id })
            .containsExactly("b", "a")
            .inOrder()
    }

    @Test
    fun downloadStateRoundTrips() = runTest {
        seed("a")

        episodes.updateDownload("a", DownloadStatus.Downloaded, 100, "/files/episodes/a.mp3")

        val downloaded = episodes.observeDownloaded().first().single().episode
        assertThat(downloaded.downloadStatus).isEqualTo(DownloadStatus.Downloaded)
        assertThat(downloaded.localFilePath).isEqualTo("/files/episodes/a.mp3")
    }
}
