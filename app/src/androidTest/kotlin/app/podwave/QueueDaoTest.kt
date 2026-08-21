package app.podwave

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.podwave.data.db.EpisodeEntity
import app.podwave.data.db.PodcastEntity
import app.podwave.data.db.PodwaveDatabase
import app.podwave.data.repository.QueueRepository
import app.podwave.rss.EpisodeType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueueDaoTest {
    private lateinit var db: PodwaveDatabase
    private lateinit var queue: QueueRepository

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PodwaveDatabase::class.java).build()
        queue = QueueRepository(db.queueDao())
        val podcastId = db.podcastDao().insert(
            PodcastEntity(
                feedUrl = "https://example.com/feed",
                title = "Show",
                author = null,
                description = null,
                artworkUrl = "https://example.com/art.jpg",
                link = null,
                categories = "",
                subscribedAt = 0,
                lastRefreshedAt = null,
                lastRefreshError = null,
            ),
        )
        db.episodeDao().insertAll(
            (1..4).map { n ->
                EpisodeEntity(
                    id = n.toLong(),
                    podcastId = podcastId,
                    guid = "ep-$n",
                    title = "Episode $n",
                    description = null,
                    audioUrl = "https://example.com/$n.mp3",
                    audioType = null,
                    durationSeconds = 600,
                    publishedAt = n.toLong(),
                    artworkUrl = null,
                    season = null,
                    episodeNumber = n,
                    type = EpisodeType.Full,
                    discoveredAt = 0,
                )
            },
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun queueOperationsKeepPositionsContiguous() = runTest {
        queue.add(1)
        queue.add(2)
        queue.add(3)
        queue.playNext(4, currentId = 1)
        queue.move(from = 3, to = 0)

        assertThat(queue.ids()).containsExactly(3L, 1L, 4L, 2L).inOrder()
        val observed = queue.observe().first()
        assertThat(observed.map { it.id }).containsExactly(3L, 1L, 4L, 2L).inOrder()
        assertThat(observed.first().artworkUrl).isEqualTo("https://example.com/art.jpg")
    }

    @Test
    fun deletingAPodcastEmptiesItsQueueEntries() = runTest {
        queue.add(1)
        queue.add(2)
        db.podcastDao().delete(db.podcastDao().getAll().single().id)
        assertThat(queue.ids()).isEmpty()
    }
}
