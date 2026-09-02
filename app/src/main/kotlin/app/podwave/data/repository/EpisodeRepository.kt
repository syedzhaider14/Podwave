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

