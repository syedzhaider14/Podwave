package app.podwave.data.repository

import app.podwave.data.db.QueueDao
import app.podwave.data.db.toDomain
import app.podwave.domain.Episode
import app.podwave.domain.QueueOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** "Up next" queue. All edits go through [QueueOrder] and are serialised with a mutex. */
class QueueRepository(private val dao: QueueDao) {
    private val mutex = Mutex()

    fun observe(): Flow<List<Episode>> = dao.observeQueue().map { list -> list.map { it.toDomain() } }

    suspend fun ids(): List<Long> = dao.episodeIds()

    suspend fun add(episodeId: Long) = edit { QueueOrder.add(it, episodeId) }

    suspend fun playNext(episodeId: Long, currentId: Long?) = edit { QueueOrder.playNext(it, episodeId, currentId) }

    suspend fun remove(episodeId: Long) = edit { QueueOrder.remove(it, episodeId) }

    suspend fun move(from: Int, to: Int) = edit { QueueOrder.move(it, from, to) }

    suspend fun clear() = edit { emptyList() }

    suspend fun next(currentId: Long?): Long? = QueueOrder.next(dao.episodeIds(), currentId)

    private suspend fun edit(transform: (List<Long>) -> List<Long>) {
        mutex.withLock {
            val current = dao.episodeIds()
            val updated = transform(current)
            if (updated != current) dao.replace(updated)
        }
    }
}
