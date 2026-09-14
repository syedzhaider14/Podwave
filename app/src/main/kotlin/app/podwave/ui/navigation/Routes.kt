package app.podwave.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object LibraryRoute

@Serializable data object DiscoverRoute

@Serializable data object QueueRoute

@Serializable data object SettingsRoute

@Serializable data object PlayerRoute

@Serializable data class PodcastRoute(val podcastId: Long)

@Serializable data class EpisodeRoute(val episodeId: Long)
