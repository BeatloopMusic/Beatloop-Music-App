package com.beatloop.music.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.beatloop.music.ui.components.BottomNavigationBar
import com.beatloop.music.ui.components.MiniPlayer
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
        Screen.Home.route
    } else {
        Screen.Onboarding.route
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
    
    val shouldShowMiniPlayer = remember(playerConnection, showFullPlayer, shouldShowBottomBar) {
        playerConnection?.currentMediaItem != null && !showFullPlayer && shouldShowBottomBar
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        BackHandler(enabled = showFullPlayer) {
            showFullPlayer = false
        }

        Scaffold(
            bottomBar = {
                AnimatedVisibility(
                    visible = shouldShowBottomBar,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    BottomNavigationBar(
                        navController = navController,
                        currentRoute = currentRoute
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
                
                // Mini Player
                AnimatedVisibility(
                    visible = shouldShowMiniPlayer == true,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    MiniPlayer(
                        playerConnection = playerConnection,
                        onClick = { showFullPlayer = true }
                    )
                }
            }
        }
        
        // Full Screen Player
        AnimatedVisibility(
            visible = showFullPlayer,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(300)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(300)
            ) + fadeOut()
        ) {
            PlayerScreen(
                onDismiss = { showFullPlayer = false }
            )
        }
    }
}
