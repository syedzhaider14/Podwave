package app.podwave.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.podwave.domain.Episode

internal const val EXTRA_PODCAST_ID = "podwave.podcast_id"

/** Media3 item for an episode. The media id is the episode id, used to save progress. */
fun Episode.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(playbackUri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(podcastTitle)
            .setAlbumTitle(podcastTitle)
            .setArtworkUri(artworkUrl?.let(Uri::parse))
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(Bundle().apply { putLong(EXTRA_PODCAST_ID, podcastId) })
            .build(),
    )
    .build()

val MediaItem.episodeId: Long? get() = mediaId.toLongOrNull()
