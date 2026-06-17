package dev.podwave.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QueueOrderTest {
    private val queue = listOf("a", "b", "c", "d")

    @Test
    fun `moves items down and up`() {
        assertThat(QueueOrder.move(queue, from = 0, to = 2)).containsExactly("b", "c", "a", "d").inOrder()
        assertThat(QueueOrder.move(queue, from = 3, to = 0)).containsExactly("d", "a", "b", "c").inOrder()
    }

    @Test
    fun `clamps targets past either end`() {
        assertThat(QueueOrder.move(queue, from = 0, to = 10)).containsExactly("b", "c", "d", "a").inOrder()
        assertThat(QueueOrder.move(queue, from = 2, to = -3)).containsExactly("c", "a", "b", "d").inOrder()
    }

    @Test
    fun `ignores moves that change nothing or point nowhere`() {
        assertThat(QueueOrder.move(queue, from = 1, to = 1)).isEqualTo(queue)
        assertThat(QueueOrder.move(queue, from = 7, to = 0)).isEqualTo(queue)
        assertThat(QueueOrder.move(emptyList<String>(), from = 0, to = 0)).isEmpty()
    }
}
