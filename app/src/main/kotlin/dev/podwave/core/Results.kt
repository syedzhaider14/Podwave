package dev.podwave.core

import kotlinx.coroutines.CancellationException

/**
 * Like [runCatching], but lets cancellation propagate. Catching [CancellationException] would
 * keep a cancelled coroutine running as if nothing happened.
 */
inline fun <T> suspendRunCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Exception) {
    Result.failure(error)
}
