package com.beatloop.music.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.beatloop.music.ui.navigation.Screen

@Composable
fun BottomNavigationBar(
    navController: NavController,
    currentRoute: String?,
    modifier: Modifier = Modifier
) {
    val effectiveRoute = currentRoute ?: navController.currentDestination?.route
    val normalizedRoute = effectiveRoute
        ?.substringBefore("?")
        ?.substringBefore("/")

    PremiumGlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        tonalElevation = 6.dp
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        )
                    )
                )
        ) {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp
            ) {
                val items = try {
                    Screen.bottomNavItems
                } catch (e: Exception) {
                    emptyList()
                }

                for (i in items.indices) {
                    val screen = items.getOrNull(i) ?: continue
                    val route = screen.route
                    if (route.isBlank()) {
                        continue
                    }

                    val itemBaseRoute = route.substringBefore("/")
                    val selected = normalizedRoute == itemBaseRoute
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1f else 0.95f,
                        animationSpec = tween(220),
                        label = "bottom_nav_item_scale"
                    )

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            val iconVector = if (selected) {
                                screen.selectedIcon ?: Icons.Filled.Home
                            } else {
                                screen.unselectedIcon ?: Icons.Outlined.Home
                            }
                            Icon(
                                imageVector = iconVector,
                                contentDescription = screen.title,
                                modifier = Modifier
                                    .size(24.dp)
                                    .scale(scale)
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        )
                    )
                }
            }
        }
    }
}
