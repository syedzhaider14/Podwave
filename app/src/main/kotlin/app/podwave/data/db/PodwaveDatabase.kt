package app.podwave.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PodcastEntity::class, EpisodeEntity::class, QueueEntryEntity::class],
    version = 1,
    exportSchema = true,
)
