package dev.podwave.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.ui.graphics.vector.ImageVector
import dev.podwave.R
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object DiscoverRoute

@Serializable
data object LibraryRoute

@Serializable
data object SettingsRoute

@Serializable
data object PlayerRoute

/**
 * A podcast is opened either by feed URL (from the library) or by directory id (from the top
 * chart, which has no feed URLs). [title] and [artworkUrl] let the screen show something
 * immediately while the feed loads.
 */
@Serializable
data class PodcastRoute(
    val feedUrl: String? = null,
    val itunesId: Long = 0,
    val title: String = "",
    val artworkUrl: String? = null,
)

@Serializable
data class EpisodeRoute(val episodeId: String)

enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Home(HomeRoute, R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    Discover(DiscoverRoute, R.string.nav_discover, Icons.Outlined.Explore, Icons.Filled.Explore),
    Library(LibraryRoute, R.string.nav_library, Icons.Outlined.Subscriptions, Icons.Filled.Subscriptions),
    Settings(SettingsRoute, R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
}
