package app.podwave.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.podwave.playback.PlayerController
import app.podwave.ui.components.MiniPlayer
import app.podwave.ui.discover.DiscoverScreen
import app.podwave.ui.episode.EpisodeScreen
import app.podwave.ui.library.LibraryScreen
import app.podwave.ui.player.PlayerScreen
import app.podwave.ui.podcast.PodcastScreen
import app.podwave.ui.queue.QueueScreen
import app.podwave.ui.settings.SettingsScreen
import org.koin.compose.koinInject
import kotlin.reflect.KClass

private enum class Tab(val route: Any, val routeClass: KClass<*>, val label: String, val selected: ImageVector, val unselected: ImageVector) {
    Library(LibraryRoute, LibraryRoute::class, "Library", Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic),
    Discover(DiscoverRoute, DiscoverRoute::class, "Discover", Icons.Rounded.Explore, Icons.Outlined.Explore),
    Queue(QueueRoute, QueueRoute::class, "Queue", Icons.AutoMirrored.Rounded.QueueMusic, Icons.AutoMirrored.Outlined.QueueMusic),
}

@Composable
fun PodwaveNavHost(
    appVersion: String,
    navController: NavHostController = rememberNavController(),
    player: PlayerController = koinInject(),
) {
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val onTab = Tab.entries.any { tab -> destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true }
    val onPlayer = destination?.hasRoute(PlayerRoute::class) == true
    val playerState by player.state.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            Column {
                AnimatedVisibility(
                    visible = playerState.hasMedia && !onPlayer,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                ) {
                    MiniPlayer(
                        state = playerState,
                        onOpen = { navController.navigate(PlayerRoute) { launchSingleTop = true } },
                        onTogglePlay = player::togglePlayPause,
                        onSkipForward = player::seekForward,
                    )
                }
                if (onTab) {
                    NavigationBar {
                        Tab.entries.forEach { tab ->
                            val selected = destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(if (selected) tab.selected else tab.unselected, contentDescription = null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = LibraryRoute,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable<LibraryRoute> {
                LibraryScreen(
                    onPodcastClick = { navController.navigate(PodcastRoute(it)) },
                    onEpisodeClick = { navController.navigate(EpisodeRoute(it)) },
                    onDiscoverClick = { navController.navigate(DiscoverRoute) },
                    onSettingsClick = { navController.navigate(SettingsRoute) },
                )
            }
            composable<DiscoverRoute> {
                DiscoverScreen(onOpenPodcast = { navController.navigate(PodcastRoute(it)) })
            }
            composable<QueueRoute> {
                QueueScreen(onEpisodeClick = { navController.navigate(EpisodeRoute(it)) })
            }
            composable<PodcastRoute> {
                PodcastScreen(
                    onBack = navController::popBackStack,
                    onEpisodeClick = { navController.navigate(EpisodeRoute(it)) },
                )
            }
            composable<EpisodeRoute> { EpisodeScreen(onBack = navController::popBackStack) }
            composable<PlayerRoute> { PlayerScreen(onClose = navController::popBackStack) }
            composable<SettingsRoute> { SettingsScreen(onBack = navController::popBackStack, appVersion = appVersion) }
        }
    }
}
