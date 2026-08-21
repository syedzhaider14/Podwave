package app.podwave.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PodcastEntity::class, EpisodeEntity::class, QueueEntryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PodwaveDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao

    abstract fun episodeDao(): EpisodeDao

    abstract fun queueDao(): QueueDao

    companion object {
        const val NAME = "podwave.db"
    }
}
