package app.podwave.domain

/** Rules for when an episode counts as played and where playback resumes. */
object PlaybackProgressPolicy {
    /** Listening to 95% of an episode marks it played… */
    const val COMPLETION_RATIO = 0.95

    /** …as does reaching the last 30 seconds of anything longer than two minutes (outros, ads). */
    const val COMPLETION_REMAINING_MS = 30_000L
    private const val LONG_EPISODE_MS = 120_000L

    /** Positions shorter than this aren't worth resuming from. */
    const val MIN_RESUME_MS = 5_000L

    /** Resume a little earlier than where the listener stopped, to regain context. */
    const val RESUME_REWIND_MS = 3_000L

    fun isCompleted(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0 || positionMs <= 0) return false
        if (positionMs >= durationMs * COMPLETION_RATIO) return true
        return durationMs > LONG_EPISODE_MS && durationMs - positionMs <= COMPLETION_REMAINING_MS
    }

    fun resumePosition(savedMs: Long, durationMs: Long?, completed: Boolean): Long {
        if (completed || savedMs < MIN_RESUME_MS) return 0
        if (durationMs != null && durationMs > 0 && isCompleted(savedMs, durationMs)) return 0
        return (savedMs - RESUME_REWIND_MS).coerceAtLeast(0)
    }
}
