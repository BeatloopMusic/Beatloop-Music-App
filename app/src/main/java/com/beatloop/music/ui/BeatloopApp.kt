package com.beatloop.music.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.beatloop.music.ui.components.BottomNavigationBar
import com.beatloop.music.ui.components.MiniPlayer
import com.beatloop.music.ui.components.PremiumScreenBackground
import com.beatloop.music.ui.navigation.BeatloopGraph
import com.beatloop.music.ui.navigation.BeatloopNavHost
import com.beatloop.music.ui.navigation.Screen
import com.beatloop.music.ui.screens.PlayerScreen
import com.beatloop.music.ui.viewmodel.AppEntryViewModel

@Composable
fun BeatloopApp() {
    val appEntryViewModel: AppEntryViewModel = hiltViewModel()
    val appEntryState by appEntryViewModel.state.collectAsState()
    val onboardingCompleted = appEntryState.onboardingCompleted

    if (onboardingCompleted == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val startDestination = if (onboardingCompleted) {
        BeatloopGraph.Main
    } else {
        BeatloopGraph.Onboarding
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val normalizedRoute = currentRoute
        ?.substringBefore("?")
        ?.substringBefore("/")

    val playerConnection = LocalPlayerConnection.current
    var showFullPlayer by rememberSaveable { mutableStateOf(false) }
    val playRequestVersion by (playerConnection?.playRequestVersion?.collectAsState()
        ?: remember { mutableStateOf(0) })

    LaunchedEffect(playRequestVersion) {
        if (playRequestVersion > 0) {
            showFullPlayer = true
        }
    }

    val topLevelRoutes = remember {
        setOf(
            Screen.Home.route,
            Screen.Search.route,
            Screen.Library.route,
            Screen.Settings.route
        )
    }

    val shouldShowBottomBar = remember(normalizedRoute, onboardingCompleted, topLevelRoutes) {
        (normalizedRoute == null && onboardingCompleted) || normalizedRoute in topLevelRoutes
    }

    val shouldShowMiniPlayer = remember(playerConnection, showFullPlayer, normalizedRoute) {
        playerConnection?.currentMediaItem != null &&
            !showFullPlayer &&
            normalizedRoute != Screen.Onboarding.route
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BackHandler(enabled = showFullPlayer) {
            showFullPlayer = false
        }

        PremiumScreenBackground {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    AnimatedVisibility(
                        visible = shouldShowBottomBar,
                        enter = fadeIn(animationSpec = tween(220)) + slideInVertically(
                            animationSpec = tween(260),
                            initialOffsetY = { it / 2 }
                        ),
                        exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(
                            animationSpec = tween(220),
                            targetOffsetY = { it / 2 }
                        )
                    ) {
                        BottomNavigationBar(
                            navController = navController,
                            currentRoute = currentRoute,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .navigationBarsPadding()
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    BeatloopNavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = shouldShowMiniPlayer,
            enter = fadeIn(animationSpec = tween(220)) + slideInVertically(
                animationSpec = tween(260),
                initialOffsetY = { it / 2 }
            ) + scaleIn(initialScale = 0.96f),
            exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(
                animationSpec = tween(220),
                targetOffsetY = { it / 2 }
            ) + scaleOut(targetScale = 0.96f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(bottom = if (shouldShowBottomBar) 84.dp else 12.dp)
        ) {
            MiniPlayer(
                playerConnection = playerConnection,
                onClick = { showFullPlayer = true }
            )
        }

        AnimatedVisibility(
            visible = showFullPlayer,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(340)
            ) + fadeIn(animationSpec = tween(280)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(280)
            ) + fadeOut(animationSpec = tween(220))
        ) {
            PlayerScreen(
                navController = navController,
                onDismiss = { showFullPlayer = false }
            )
        }
    }
}
