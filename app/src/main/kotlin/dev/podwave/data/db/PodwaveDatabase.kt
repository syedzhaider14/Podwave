package dev.podwave.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant

@Database(
    entities = [PodcastEntity::class, EpisodeEntity::class, QueueEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PodwaveDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao

