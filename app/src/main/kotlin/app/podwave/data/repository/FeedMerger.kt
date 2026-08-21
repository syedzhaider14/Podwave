package app.podwave.data.repository

import app.podwave.data.db.EpisodeEntity
import app.podwave.rss.ParsedEpisode

data class MergeResult(
    val toInsert: List<EpisodeEntity>,
    val toUpdate: List<EpisodeEntity>,
)

/**
 * Reconciles freshly parsed episodes with what's stored.
 *
 * Episodes are matched by guid. New ones are inserted; existing ones get
 * their metadata refreshed (publishers fix typos, swap audio files, add
 * artwork) while listening state and downloads are preserved. Episodes that
 * disappeared from the feed are kept: the user may be halfway through one.
 */
object FeedMerger {
    fun merge(
        podcastId: Long,
        existing: List<EpisodeEntity>,
        parsed: List<ParsedEpisode>,
        now: Long,
    ): MergeResult {
        val byGuid = existing.associateBy { it.guid }
        val toInsert = mutableListOf<EpisodeEntity>()
        val toUpdate = mutableListOf<EpisodeEntity>()

        for (item in parsed) {
            val current = byGuid[item.guid]
            if (current == null) {
                toInsert += item.toEntity(podcastId, now)
                continue
            }
            val refreshed = current.copy(
                title = item.title,
                description = item.description,
                audioUrl = item.audioUrl,
                audioType = item.audioType,
                durationSeconds = item.durationSeconds ?: current.durationSeconds,
                publishedAt = item.publishedAt?.toEpochMilli() ?: current.publishedAt,
                artworkUrl = item.artworkUrl,
                season = item.season,
                episodeNumber = item.episode,
                type = item.type,
            )
            if (refreshed != current) toUpdate += refreshed
        }
        return MergeResult(toInsert, toUpdate)
    }

    private fun ParsedEpisode.toEntity(podcastId: Long, now: Long) = EpisodeEntity(
        podcastId = podcastId,
        guid = guid,
        title = title,
        description = description,
        audioUrl = audioUrl,
        audioType = audioType,
        durationSeconds = durationSeconds,
        publishedAt = publishedAt?.toEpochMilli(),
        artworkUrl = artworkUrl,
        season = season,
        episodeNumber = episode,
        type = type,
        discoveredAt = now,
    )
}
