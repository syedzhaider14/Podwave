package dev.podwave.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.SessionCommand
import dev.podwave.model.Episode

/** Playback starts from the beginning if the saved position is within this of the end. */
const val RESTART_THRESHOLD_MS = 30_000L

fun Episode.toMediaItem(): MediaItem {
    val uri = Uri.parse(playbackUri)
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        // Controllers in another process don't receive the URI; the session restores it from here.
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(podcastTitle)
                .setAlbumTitle(podcastTitle)
                .setArtworkUri(artworkUrl?.let(Uri::parse))
                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build(),
        )
        .build()
}

/** Where to start an episode: resume unless it is finished or almost finished. */
fun Episode.startPositionMs(): Long {
    if (isPlayed) return 0
    val duration = durationMs ?: return positionMs
    return if (duration - positionMs < RESTART_THRESHOLD_MS) 0 else positionMs
}

object PodwaveCommands {
    const val ACTION_PAUSE_AT_END = "dev.podwave.PAUSE_AT_END_OF_EPISODE"
    const val EXTRA_ENABLED = "enabled"

    val PauseAtEnd = SessionCommand(ACTION_PAUSE_AT_END, Bundle.EMPTY)

    fun pauseAtEndArgs(enabled: Boolean) = Bundle().apply { putBoolean(EXTRA_ENABLED, enabled) }
}
