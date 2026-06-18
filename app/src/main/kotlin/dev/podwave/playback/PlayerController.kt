package dev.podwave.playback

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.podwave.core.ApplicationScope
import dev.podwave.data.EpisodeRepository
import dev.podwave.data.SettingsRepository
import dev.podwave.model.Episode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class PlayerState(
    val episodeId: String? = null,
    val title: String = "",
    val podcastTitle: String = "",
    val artworkUrl: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val sleepTimer: SleepTimerState = SleepTimerState.Off,
    val sleepRemainingMs: Long? = null,
    val upNextCount: Int = 0,
    /** The current item failed to load or play (bad URL, offline, unsupported format). */
    val hasError: Boolean = false,
) {
    val hasMedia: Boolean get() = episodeId != null
}

/**
 * The UI's handle on playback. It talks to [PlaybackService] through a Media3 [MediaController]
 * (the same API Android Auto and Wear use), exposes a single [StateFlow] for Compose, and keeps
 * the player's playlist in step with the user's queue.
 */
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val episodes: EpisodeRepository,
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var connection: Deferred<MediaController>? = null

    /** Connects to the playback service. Called when the UI starts so it reflects background playback. */
    fun connect() {
        scope.launch(Dispatchers.Main) { controller() }
    }

    private suspend fun controller(): MediaController = withContext(Dispatchers.Main) {
        val existing = connection
        if (existing != null && (!existing.isCompleted || existing.getCompletedOrNull()?.isConnected == true)) {
            return@withContext existing.await()
        }
        val created = scope.async(Dispatchers.Main) {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            MediaController.Builder(context, token).buildAsync().await().also { attach(it) }
        }
        connection = created
        created.await()
    }

    private fun attach(controller: MediaController) {
        controller.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = publish(player)

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    // The "end of episode" sleep timer did its job; switch it off.
                    if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                        scope.launch { setSleepTimer(SleepTimerOption.Off) }
                    }
                }
            },
        )
        publish(controller)
        startTicker(controller)
        syncQueue(controller)
    }

    private fun publish(player: Player) {
        val metadata = player.mediaMetadata
        _state.update { current ->
            current.copy(
                episodeId = player.currentMediaItem?.mediaId,
                title = metadata.title?.toString().orEmpty(),
                podcastTitle = metadata.artist?.toString().orEmpty(),
                artworkUrl = metadata.artworkUri?.toString(),
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = player.duration.takeIf { it > 0 } ?: 0,
                speed = player.playbackParameters.speed,
                upNextCount = (player.mediaItemCount - player.currentMediaItemIndex - 1).coerceAtLeast(0),
                hasError = player.playerError != null,
