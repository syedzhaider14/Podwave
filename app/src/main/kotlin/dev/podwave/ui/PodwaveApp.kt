package dev.podwave.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.podwave.model.DirectoryPodcast
import dev.podwave.ui.discover.DiscoverScreen
import dev.podwave.ui.episode.EpisodeScreen
import dev.podwave.ui.home.HomeScreen
import dev.podwave.ui.library.LibraryScreen
import dev.podwave.ui.navigation.DiscoverRoute
import dev.podwave.ui.navigation.EpisodeRoute
import dev.podwave.ui.navigation.HomeRoute
import dev.podwave.ui.navigation.LibraryRoute
import dev.podwave.ui.navigation.PlayerRoute
import dev.podwave.ui.navigation.PodcastRoute
import dev.podwave.ui.navigation.SettingsRoute
import dev.podwave.ui.navigation.TopLevelDestination
import dev.podwave.ui.player.MiniPlayer
import dev.podwave.ui.player.PlayerScreen
import dev.podwave.ui.player.PlayerViewModel
import dev.podwave.ui.podcast.PodcastScreen
import dev.podwave.ui.settings.SettingsScreen

@Composable
fun PodwaveApp(playerViewModel: PlayerViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val player by playerViewModel.state.collectAsStateWithLifecycle()
    val skip by playerViewModel.skipIntervals.collectAsStateWithLifecycle()

    // The full player covers the whole screen, so it hides the bottom chrome.
    val showChrome = destination?.hasRoute<PlayerRoute>() != true

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showChrome) {
                Column {
                    AnimatedVisibility(
                        visible = player.hasMedia,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        MiniPlayer(
                            state = player,
                            skipForwardSeconds = skip.forwardSeconds,
                            onOpen = { navController.navigate(PlayerRoute) { launchSingleTop = true } },
                            onPlayPause = playerViewModel::togglePlayPause,
                            onSkipForward = playerViewModel::skipForward,
                        )
                    }
                    NavigationBar {
                        TopLevelDestination.entries.forEach { item ->
                            val selected = destination.isInHierarchy(item)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navController.navigateToTopLevel(item.route) },
                                icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null) },
                                label = { Text(stringResource(item.label)) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            val openEpisode: (String) -> Unit = { id -> navController.navigate(EpisodeRoute(id)) }
            val openFeed: (String) -> Unit = { feedUrl -> navController.navigate(PodcastRoute(feedUrl = feedUrl)) }
            val openDiscover = { navController.navigateToTopLevel(DiscoverRoute) }

            composable<HomeRoute> {
                HomeScreen(onOpenEpisode = openEpisode, onDiscover = openDiscover)
            }
            composable<DiscoverRoute> {
                DiscoverScreen(onOpenPodcast = { navController.navigate(it.toRoute()) })
            }
            composable<LibraryRoute> {
                LibraryScreen(onOpenPodcast = openFeed, onOpenEpisode = openEpisode, onDiscover = openDiscover)
            }
            composable<SettingsRoute> {
                SettingsScreen()
            }
            composable<PodcastRoute> {
                PodcastScreen(onBack = navController::navigateUp, onOpenEpisode = openEpisode)
            }
            composable<EpisodeRoute> {
                EpisodeScreen(onBack = navController::navigateUp, onOpenPodcast = openFeed)
            }
            composable<PlayerRoute>(
                enterTransition = { slideInVertically(initialOffsetY = { it }) },
                exitTransition = { fadeOut() },
                popEnterTransition = { fadeIn() },
                popExitTransition = { slideOutVertically(targetOffsetY = { it }) },
            ) {
                PlayerScreen(onClose = navController::navigateUp, onOpenEpisode = openEpisode)
            }
        }
    }
}

private fun DirectoryPodcast.toRoute() =
    PodcastRoute(feedUrl = feedUrl, itunesId = itunesId, title = title, artworkUrl = artworkUrl)

private fun NavDestination?.isInHierarchy(item: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(item.route::class) } == true

/** Bottom-bar navigation: one copy of each tab, each keeping its own back stack and scroll. */
private fun NavController.navigateToTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
