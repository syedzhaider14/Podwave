package app.podwave.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.podwave.MainActivity
import app.podwave.data.repository.EpisodeRepository
import app.podwave.data.repository.QueueRepository
import app.podwave.domain.SleepTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject

/**
 * Hosts the player in a foreground service so audio keeps going with the
 * screen off or the app in the background. Media3 handles the media
 * notification, lock-screen controls, Bluetooth buttons and Android Auto
 * plumbing through the [MediaSession].
 *
 * The service also owns "what happens next": saving progress while playing
 * and advancing through the queue when an episode ends, so it works even
 * when no UI is running.
 */
class PlaybackService : MediaSessionService() {

    private val episodes: EpisodeRepository by inject()
    private val queue: QueueRepository by inject()
    private val sleepTimer: SleepTimer by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null
    private var progressJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(SEEK_BACK_MS)
            .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
            .build()
        player.addListener(PlayerListener(player))

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiping the app away stops playback only if nothing is playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.player?.let { player ->
            // Last chance to persist the position; bounded so shutdown can't hang.
            runBlocking { withTimeoutOrNull(500) { saveProgress(player) } }
            player.release()
        }
        session?.release()
        session = null
        scope.cancel()
        super.onDestroy()
    }

    private inner class PlayerListener(private val player: Player) : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            progressJob?.cancel()
            if (isPlaying) {
                progressJob = scope.launch {
                    while (isActive) {
                        delay(SAVE_INTERVAL_MS)
                        saveProgress(player)
                        if (sleepTimer.shouldPauseNow()) player.pause()
                    }
                }
            } else {
                scope.launch { saveProgress(player) }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) scope.launch { onEpisodeEnded(player) }
        }
    }

    private suspend fun saveProgress(player: Player) {
        val id = player.currentMediaItem?.episodeId ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: return
        episodes.savePlayback(id, player.currentPosition, duration)
    }

    private suspend fun onEpisodeEnded(player: Player) {
        val finishedId = player.currentMediaItem?.episodeId ?: return
        episodes.setPlayed(finishedId, played = true)
        val nextId = queue.next(finishedId)
        queue.remove(finishedId)

        if (sleepTimer.onEpisodeEnded()) return
        val next = nextId?.let { episodes.get(it) } ?: return
        player.setMediaItem(next.toMediaItem(), 0L)
        player.prepare()
        player.play()
    }

    companion object {
        const val SEEK_BACK_MS = 10_000L
        const val SEEK_FORWARD_MS = 30_000L
        private const val SAVE_INTERVAL_MS = 5_000L
    }
}
