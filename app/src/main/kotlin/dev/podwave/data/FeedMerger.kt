package dev.podwave.data

import dev.podwave.data.db.EpisodeEntity
import dev.podwave.model.DownloadStatus
import dev.podwave.model.episodeId
import dev.podwave.rss.ParsedEpisode
import java.time.Instant

data class MergeResult(
    val episodes: List<EpisodeEntity>,
    /** Ids that were not in the database before this refresh, newest first. */
    val newEpisodeIds: List<String>,
)

/**
 * Combines a freshly parsed feed with what is already stored.
 *
 * Feed metadata (title, notes, audio URL) always comes from the feed, because publishers fix
 * typos and move hosting. Listener state (position, played, downloads) always comes from the
 * database, so a refresh never loses progress. Episodes that disappeared from the feed are left
 * untouched rather than deleted, since the user may have downloaded or half-played them.
 */
object FeedMerger {
    fun merge(
        feedUrl: String,
        parsed: List<ParsedEpisode>,
        existing: List<EpisodeEntity>,
        now: Instant,
    ): MergeResult {
        val existingById = existing.associateBy { it.id }
        val newIds = mutableListOf<String>()

        val merged = parsed
            .map { episodeId(feedUrl, it.guid) to it }
            .distinctBy { (id, _) -> id }
            .map { (id, item) ->
                val previous = existingById[id]
                if (previous == null) newIds += id
                EpisodeEntity(
                    id = id,
                    podcastFeedUrl = feedUrl,
                    guid = item.guid,
                    title = item.title,
                    description = item.description,
                    audioUrl = item.audioUrl,
                    audioMimeType = item.audioMimeType,
                    audioSizeBytes = item.audioSizeBytes,
                    durationMs = item.durationMs ?: previous?.durationMs,
                    publishedAt = item.publishedAt ?: previous?.publishedAt,
                    artworkUrl = item.artworkUrl,
                    season = item.season,
                    episodeNumber = item.episodeNumber,
                    explicit = item.explicit,
                    positionMs = previous?.positionMs ?: 0,
                    playedAt = previous?.playedAt,
                    lastPlayedAt = previous?.lastPlayedAt,
                    downloadStatus = previous?.downloadStatus ?: DownloadStatus.None,
                    downloadProgress = previous?.downloadProgress ?: 0,
                    localFilePath = previous?.localFilePath,
                    addedAt = previous?.addedAt ?: now,
                )
            }

        val publishedOrder = merged.associate { it.id to (it.publishedAt ?: Instant.EPOCH) }
        return MergeResult(
            episodes = merged,
            newEpisodeIds = newIds.sortedByDescending { publishedOrder[it] },
        )
    }

    /**
     * When subscribing to an existing show, everything in the back catalogue is "new". Only the
     * latest few should be treated as new so we don't auto-download a decade of episodes.
     */
    fun newEpisodesToAnnounce(result: MergeResult, isFirstFetch: Boolean, limit: Int = 1): List<String> =
        if (isFirstFetch) result.newEpisodeIds.take(limit) else result.newEpisodeIds
}
