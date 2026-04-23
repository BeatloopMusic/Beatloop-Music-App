package com.beatloop.music.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector? = null,
    val unselectedIcon: ImageVector? = null
) {
    data object Onboarding : Screen(
        route = "onboarding",
        title = "Onboarding"
    )

    // Main Navigation
    data object Home : Screen(
        route = "home",
        title = "Home",
        selectedIcon = Icons.Rounded.Home,
        unselectedIcon = Icons.Outlined.Home
    )
    
    data object Search : Screen(
        route = "search",
        title = "Search",
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search
    )
    
    data object Library : Screen(
        route = "library",
        title = "Library",
        selectedIcon = Icons.Filled.LibraryMusic,
        unselectedIcon = Icons.Outlined.LibraryMusic
    )
    
    data object Settings : Screen(
        route = "settings",
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )

    data object Changelog : Screen(
        route = "changelog",
        title = "Changelog"
    )
    
    // Detail Screens
    data object Artist : Screen(
        route = "artist/{artistId}",
        title = "Artist"
    ) {
        fun createRoute(artistId: String) = "artist/$artistId"
    }
    
    data object Album : Screen(
        route = "album/{albumId}",
        title = "Album"
    ) {
        fun createRoute(albumId: String) = "album/$albumId"
    }
    
    data object Playlist : Screen(
        route = "playlist/{playlistId}",
        title = "Playlist"
    ) {
        fun createRoute(playlistId: String) = "playlist/$playlistId"
    }
    
    data object LocalPlaylist : Screen(
        route = "local_playlist/{playlistId}",
        title = "Playlist"
    ) {
        fun createRoute(playlistId: Long) = "local_playlist/$playlistId"
    }
    
    data object Queue : Screen(
        route = "queue",
        title = "Queue"
    )
    
    data object Downloads : Screen(
        route = "downloads",
        title = "Downloads"
    )
    
    data object History : Screen(
        route = "history",
        title = "History"
    )
    
    data object LikedSongs : Screen(
        route = "liked_songs",
        title = "Liked Songs"
    )
    
    data object MoodsAndGenres : Screen(
        route = "moods_genres/{params}",
        title = "Moods & Genres"
    ) {
        fun createRoute(params: String) = "moods_genres/$params"
    }
    
    data object Charts : Screen(
        route = "charts",
        title = "Charts"
    )
    
    companion object {
        val bottomNavItems get() = listOf(Home, Search, Library, Settings)
    }
}
