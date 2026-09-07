package app.podwave

import app.podwave.domain.QueueOrder
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QueueOrderTest {
    private val queue = listOf(1L, 2L, 3L, 4L)

    @Test
    fun `add appends once`() {
        assertThat(QueueOrder.add(queue, 5)).containsExactly(1L, 2L, 3L, 4L, 5L).inOrder()
        assertThat(QueueOrder.add(queue, 2)).isSameInstanceAs(queue)
    }

    @Test
    fun `play next inserts after the current episode`() {
        assertThat(QueueOrder.playNext(queue, 9, currentId = 2)).containsExactly(1L, 2L, 9L, 3L, 4L).inOrder()
    }

    @Test
    fun `play next goes to the front when nothing queued is playing`() {
        assertThat(QueueOrder.playNext(queue, 9, currentId = null)).containsExactly(9L, 1L, 2L, 3L, 4L).inOrder()
        assertThat(QueueOrder.playNext(queue, 9, currentId = 42)).containsExactly(9L, 1L, 2L, 3L, 4L).inOrder()
    }

    @Test
    fun `play next moves an already queued episode`() {
        assertThat(QueueOrder.playNext(queue, 4, currentId = 1)).containsExactly(1L, 4L, 2L, 3L).inOrder()
    }

    @Test
    fun `move reorders and clamps`() {
        assertThat(QueueOrder.move(queue, from = 0, to = 2)).containsExactly(2L, 3L, 1L, 4L).inOrder()
        assertThat(QueueOrder.move(queue, from = 3, to = 0)).containsExactly(4L, 1L, 2L, 3L).inOrder()
        assertThat(QueueOrder.move(queue, from = 1, to = 99)).containsExactly(1L, 3L, 4L, 2L).inOrder()
        assertThat(QueueOrder.move(queue, from = 9, to = 0)).isEqualTo(queue)
    }

    @Test
    fun `next follows the current episode`() {
        assertThat(QueueOrder.next(queue, currentId = 2)).isEqualTo(3L)
        assertThat(QueueOrder.next(queue, currentId = 4)).isNull()
        assertThat(QueueOrder.next(queue, currentId = null)).isEqualTo(1L)
        assertThat(QueueOrder.next(queue, currentId = 42)).isEqualTo(1L)
        assertThat(QueueOrder.next(emptyList(), currentId = null)).isNull()
    }

    @Test
    fun `remove drops the episode`() {
        assertThat(QueueOrder.remove(queue, 3)).containsExactly(1L, 2L, 4L).inOrder()
    }
}
