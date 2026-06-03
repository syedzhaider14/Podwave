package dev.podwave.model

import java.security.MessageDigest

/**
 * Episode ids must stay the same across feed refreshes so playback progress and downloads
 * survive. Guids are only unique within one feed, so the id hashes the feed URL with the guid.
 */
fun episodeId(feedUrl: String, guid: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest("$feedUrl|$guid".toByteArray(Charsets.UTF_8))
    return digest.take(12).joinToString("") { "%02x".format(it) }
}
