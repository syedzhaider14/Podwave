package app.podwave.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.podwave.data.repository.EpisodeRepository
import app.podwave.data.repository.QueueRepository
import app.podwave.data.settings.SettingsRepository
import app.podwave.domain.Episode
import app.podwave.domain.PlaybackProgressPolicy
import app.podwave.domain.SleepTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PlayerState(
    val episodeId: Long? = null,
    val title: String = "",
    val podcastTitle: String = "",
    val artworkUrl: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
) {
    val hasMedia: Boolean get() = episodeId != null
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * App-side handle to the [PlaybackService]. Connects lazily through a
 * [MediaController] and mirrors the player into a [StateFlow] for Compose.
 */
class PlayerController(
    private val context: Context,
    private val episodes: EpisodeRepository,
    private val queue: QueueRepository,
    private val settings: SettingsRepository,
    val sleepTimer: SleepTimer,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val connectMutex = Mutex()
    private var controller: MediaController? = null
    private var positionJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    /** Connects to the service if needed. Safe to call repeatedly. */
    suspend fun connect(): MediaController = connectMutex.withLock {
        controller ?: MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
            .buildAsync()
            .await()
            .also { created ->
                controller = created
                created.addListener(listener)
                publish(created)
            }
    }

    fun play(episode: Episode) {
        scope.launch {
            val player = connect()
            if (player.currentMediaItem?.episodeId == episode.id) {
                player.play()
                return@launch
            }
            val start = PlaybackProgressPolicy.resumePosition(episode.playbackPositionMs, episode.durationMs, episode.completed)
            player.setMediaItem(episode.toMediaItem(), start)
            player.setPlaybackSpeed(settings.settings.first().playbackSpeed)
            player.prepare()
            player.play()
        }
    }

    fun togglePlayPause() {
        scope.launch {
            val player = connect()
            if (player.isPlaying) player.pause() else player.play()
        }
    }

    fun seekBack() = withController { it.seekBack() }

    fun seekForward() = withController { it.seekForward() }

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs.coerceAtLeast(0)) }

    fun setSpeed(speed: Float) {
        scope.launch {
            settings.setPlaybackSpeed(speed)
            connect().setPlaybackSpeed(speed)
        }
    }

    /** Skips to the next queued episode, marking the current one as played. */
    fun skipToNext() {
        scope.launch {
            val currentId = _state.value.episodeId
            val nextId = queue.next(currentId) ?: return@launch
            currentId?.let {
                episodes.setPlayed(it, played = true)
                queue.remove(it)
            }
            episodes.get(nextId)?.let(::play)
        }
    }

    private fun withController(action: (MediaController) -> Unit) {
        scope.launch { action(connect()) }
    }

    private fun publish(player: Player) {
        val item = player.currentMediaItem
        _state.update {
            PlayerState(
                episodeId = item?.episodeId,
                title = item?.mediaMetadata?.title?.toString().orEmpty(),
                podcastTitle = item?.mediaMetadata?.artist?.toString().orEmpty(),
                artworkUrl = item?.mediaMetadata?.artworkUri?.toString(),
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0,
                speed = player.playbackParameters.speed,
            )
        }
        // Position isn't an event, so poll it while playing.
        if (player.isPlaying && positionJob?.isActive != true) {
            positionJob = scope.launch {
                while (isActive && player.isPlaying) {
                    _state.update { it.copy(positionMs = player.currentPosition.coerceAtLeast(0)) }
                    delay(POSITION_POLL_MS)
                }
            }
        }
    }

    companion object {
        private const val POSITION_POLL_MS = 500L
    }
}
