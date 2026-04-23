package com.beatloop.music.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.beatloop.music.ui.onboarding.OnboardingScreen
import com.beatloop.music.ui.screens.AlbumScreen
import com.beatloop.music.ui.screens.ArtistScreen
import com.beatloop.music.ui.screens.DownloadsScreen
import com.beatloop.music.ui.screens.HistoryScreen
import com.beatloop.music.ui.screens.HomeScreen
import com.beatloop.music.ui.screens.LibraryScreen
import com.beatloop.music.ui.screens.LikedSongsScreen
import com.beatloop.music.ui.screens.LocalPlaylistScreen
import com.beatloop.music.ui.screens.PlaylistScreen
import com.beatloop.music.ui.screens.QueueScreen
import com.beatloop.music.ui.screens.SearchScreen
import com.beatloop.music.ui.screens.SettingsScreen
import com.beatloop.music.ui.screens.ChangelogScreen

object BeatloopGraph {
    const val Onboarding = "graph_onboarding"
    const val Main = "graph_main"
    const val Player = "graph_player"
    const val Library = "graph_library"
    const val Detail = "graph_detail"
}

@Composable
fun BeatloopNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier
) {
    val topLevelRoutes = setOf(
        Screen.Home.route,
        Screen.Search.route,
        Screen.Library.route,
        Screen.Settings.route
    )

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            val fromRoute = initialState.destination.route.toBaseRoute()
            val toRoute = targetState.destination.route.toBaseRoute()
            if (fromRoute in topLevelRoutes && toRoute in topLevelRoutes) {
                fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.985f)
            } else {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(340)
                ) + fadeIn(animationSpec = tween(240))
            }
        },
        exitTransition = {
            val fromRoute = initialState.destination.route.toBaseRoute()
            val toRoute = targetState.destination.route.toBaseRoute()
            if (fromRoute in topLevelRoutes && toRoute in topLevelRoutes) {
                fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 1.01f)
            } else {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(260)
                ) + fadeOut(animationSpec = tween(200))
            }
        },
        popEnterTransition = {
            val fromRoute = initialState.destination.route.toBaseRoute()
            val toRoute = targetState.destination.route.toBaseRoute()
            if (fromRoute in topLevelRoutes && toRoute in topLevelRoutes) {
                fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.99f)
            } else {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(220))
            }
        },
        popExitTransition = {
            val fromRoute = initialState.destination.route.toBaseRoute()
            val toRoute = targetState.destination.route.toBaseRoute()
            if (fromRoute in topLevelRoutes && toRoute in topLevelRoutes) {
                fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 1.01f)
            } else {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(260)
                ) + fadeOut(animationSpec = tween(200))
            }
        }
    ) {
        onboardingGraph(navController)
        mainGraph(navController)
        playerGraph(navController)
        libraryGraph(navController)
        detailGraph(navController)
    }
}

private fun NavGraphBuilder.onboardingGraph(
    navController: NavHostController
) {
    navigation(
        route = BeatloopGraph.Onboarding,
        startDestination = Screen.Onboarding.route
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(BeatloopGraph.Main) {
                        popUpTo(BeatloopGraph.Onboarding) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}

private fun NavGraphBuilder.mainGraph(
    navController: NavHostController
) {
    navigation(
        route = BeatloopGraph.Main,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }

        composable(Screen.Search.route) {
            SearchScreen(navController = navController)
        }

        composable(Screen.Library.route) {
            LibraryScreen(navController = navController)
        }

        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
    }
}

private fun NavGraphBuilder.playerGraph(
    navController: NavHostController
) {
    navigation(
        route = BeatloopGraph.Player,
        startDestination = Screen.Queue.route
    ) {
        composable(Screen.Queue.route) {
            QueueScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

private fun NavGraphBuilder.libraryGraph(
    navController: NavHostController
) {
    navigation(
        route = BeatloopGraph.Library,
        startDestination = Screen.Downloads.route
    ) {
        composable(Screen.Downloads.route) {
            DownloadsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.LikedSongs.route) {
            LikedSongsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

private fun NavGraphBuilder.detailGraph(
    navController: NavHostController
) {
    navigation(
        route = BeatloopGraph.Detail,
        startDestination = Screen.Artist.route
    ) {
        composable(
            route = Screen.Artist.route,
            arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            }
        ) { backStackEntry ->
            val artistId = backStackEntry.arguments?.getString("artistId") ?: return@composable
            ArtistScreen(artistId = artistId, navController = navController)
        }

        composable(
            route = Screen.Album.route,
            arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            }
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getString("albumId") ?: return@composable
            AlbumScreen(albumId = albumId, navController = navController)
        }

        composable(
            route = Screen.Playlist.route,
            arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            }
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: return@composable
            PlaylistScreen(playlistId = playlistId, navController = navController)
        }

        composable(
            route = Screen.LocalPlaylist.route,
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            }
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
            LocalPlaylistScreen(playlistId = playlistId, navController = navController)
        }

        composable(route = Screen.Changelog.route) {
            ChangelogScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}

private fun String?.toBaseRoute(): String? {
    return this
        ?.substringBefore("?")
        ?.substringBefore("/")
}
