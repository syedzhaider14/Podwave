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

    abstract fun episodeDao(): EpisodeDao

    abstract fun queueDao(): QueueDao
}

class Converters {
    private val stringList = ListSerializer(String.serializer())

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun fromList(value: List<String>): String = Json.encodeToString(stringList, value)

    @TypeConverter
    fun toList(value: String): List<String> =
        runCatching { Json.decodeFromString(stringList, value) }.getOrDefault(emptyList())
}
