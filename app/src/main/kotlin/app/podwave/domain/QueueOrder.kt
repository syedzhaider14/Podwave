package app.podwave.domain

/**
 * Pure operations on the "up next" queue, represented as an ordered list of
 * episode ids. Every operation keeps ids unique.
 */
object QueueOrder {
    fun add(queue: List<Long>, id: Long): List<Long> = if (id in queue) queue else queue + id

    /**
     * Inserts [id] right after [currentId] (the episode playing now), or at
     * the front when nothing from the queue is playing. If [id] was already
     * queued it is moved.
     */
    fun playNext(queue: List<Long>, id: Long, currentId: Long?): List<Long> {
        val without = queue - id
        val anchor = currentId?.let { without.indexOf(it) } ?: -1
        return without.toMutableList().apply { add(anchor + 1, id) }
    }

    fun remove(queue: List<Long>, id: Long): List<Long> = queue - id

    /** Moves the item at [from] to index [to]; out-of-range indices are clamped. */
    fun move(queue: List<Long>, from: Int, to: Int): List<Long> {
        if (from !in queue.indices) return queue
        val list = queue.toMutableList()
        val item = list.removeAt(from)
        list.add(to.coerceIn(0, list.size), item)
        return list
    }

    /** The episode that should play after [currentId] finishes. */
    fun next(queue: List<Long>, currentId: Long?): Long? {
        if (currentId == null) return queue.firstOrNull()
        val index = queue.indexOf(currentId)
        return if (index == -1) queue.firstOrNull() else queue.getOrNull(index + 1)
    }
}
