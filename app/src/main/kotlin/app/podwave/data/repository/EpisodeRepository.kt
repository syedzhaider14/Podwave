package app.podwave.data.repository

import app.podwave.data.db.EpisodeDao
import app.podwave.data.db.toDomain
import app.podwave.domain.DownloadState
import app.podwave.domain.Episode
import app.podwave.domain.PlaybackProgressPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EpisodeRepository(
    private val dao: EpisodeDao,
    private val now: () -> Long,
) {
    fun observeForPodcast(podcastId: Long): Flow<List<Episode>> =
        dao.observeForPodcast(podcastId).map { list -> list.map { it.toDomain() } }

    fun observeLatest(limit: Int = 100): Flow<List<Episode>> =
        dao.observeLatest(limit).map { list -> list.map { it.toDomain() } }

    fun observeInProgress(): Flow<List<Episode>> = dao.observeInProgress().map { list -> list.map { it.toDomain() } }

    fun observeDownloads(): Flow<List<Episode>> = dao.observeDownloads().map { list -> list.map { it.toDomain() } }

    fun observe(id: Long): Flow<Episode?> = dao.observe(id).map { it?.toDomain() }

    suspend fun get(id: Long): Episode? = dao.get(id)?.toDomain()

    /**
     * Persists the playback position. Positions near the end mark the
     * episode as played (and reset the position so it restarts next time).
     *
     * @return true if this save marked the episode completed.
     */
    suspend fun savePlayback(id: Long, positionMs: Long, durationMs: Long): Boolean {
        val completed = PlaybackProgressPolicy.isCompleted(positionMs, durationMs)
        dao.updatePlayback(id, if (completed) 0 else positionMs.coerceAtLeast(0), completed, now())
        return completed
    }

    suspend fun setPlayed(id: Long, played: Boolean) {
        dao.updatePlayback(id, positionMs = 0, completed = played, playedAt = now())
    }

    suspend fun updateDownload(id: Long, state: DownloadState, progress: Int = 0, path: String? = null) {
        dao.updateDownload(id, state, progress.coerceIn(0, 100), path)
    }
}
