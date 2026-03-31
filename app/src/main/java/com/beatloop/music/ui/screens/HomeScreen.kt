package com.beatloop.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.beatloop.music.data.model.SongItem
import com.beatloop.music.playback.createMediaItem
import com.beatloop.music.ui.LocalPlayerConnection
import com.beatloop.music.ui.components.*
import com.beatloop.music.ui.navigation.Screen
import com.beatloop.music.ui.viewmodel.HomeViewModel
import com.beatloop.music.ui.viewmodel.SongActionsViewModel
import com.beatloop.music.utils.NetworkStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
    songActionsViewModel: SongActionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playerConnection = LocalPlayerConnection.current
    val context = LocalContext.current
    
    // Song options state
    var selectedSong by remember { mutableStateOf<SongItem?>(null) }
    var showSongOptions by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    
    val playlists by songActionsViewModel.playlists.collectAsState()
    val likedSongIds by songActionsViewModel.likedSongIds.collectAsState()
    val downloadedSongIds by songActionsViewModel.downloadedSongIds.collectAsState()
    val downloadUiStateMap by songActionsViewModel.downloadUiStateMap.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onHomeVisible()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Song Options Bottom Sheet
    if (showSongOptions && selectedSong != null) {
        SongOptionsBottomSheet(
            song = selectedSong!!,
            isLiked = likedSongIds.contains(selectedSong!!.id),
            isDownloaded = downloadedSongIds.contains(selectedSong!!.id),
            downloadProgress = downloadUiStateMap[selectedSong!!.id]
                ?.takeIf { it.state == com.beatloop.music.data.model.DownloadState.DOWNLOADING }
                ?.progress,
            downloadSizeBytes = downloadUiStateMap[selectedSong!!.id]?.fileSizeBytes,
            onDismiss = { showSongOptions = false },
            onPlayNext = {
                playerConnection?.addMediaItemNext(
                    createMediaItem(
                        id = selectedSong!!.id,
                        title = selectedSong!!.title,
                        artist = selectedSong!!.artistsText,
                        thumbnailUrl = selectedSong!!.thumbnailUrl,
                        localPath = selectedSong!!.localPath
                    )
                )
            },
            onAddToQueue = {
                playerConnection?.addMediaItem(
                    createMediaItem(
                        id = selectedSong!!.id,
                        title = selectedSong!!.title,
                        artist = selectedSong!!.artistsText,
                        thumbnailUrl = selectedSong!!.thumbnailUrl,
                        localPath = selectedSong!!.localPath
                    )
                )
            },
            onLike = {
                songActionsViewModel.toggleLike(selectedSong!!)
            },
            onAddToPlaylist = {
                showSongOptions = false
                showAddToPlaylist = true
            },
            onDownload = {
                selectedSong?.let(songActionsViewModel::downloadSong)
                navController.navigate(Screen.Downloads.route)
                showSongOptions = false
            },
            onGoToArtist = {
                selectedSong?.artistId?.let { artistId ->
                    navController.navigate(Screen.Artist.createRoute(artistId))
                }
                showSongOptions = false
            },
            onGoToAlbum = {
                selectedSong!!.albumId?.let { albumId ->
                    navController.navigate(Screen.Album.createRoute(albumId))
                }
            },
            onShare = {
                context.startActivity(songActionsViewModel.createShareIntent(selectedSong!!))
            }
        )
    }
    
    // Add to Playlist Bottom Sheet
    if (showAddToPlaylist && selectedSong != null) {
        AddToPlaylistBottomSheet(
            playlists = playlists,
            onDismiss = { showAddToPlaylist = false },
            onCreateNew = {
                showAddToPlaylist = false
                showCreatePlaylist = true
            },
            onSelectPlaylist = { playlistId ->
                songActionsViewModel.addToPlaylist(playlistId, selectedSong!!, context)
            }
        )
    }
    
    // Create Playlist Dialog
    if (showCreatePlaylist && selectedSong != null) {
        var playlistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false },
            title = { Text("Create Playlist") },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Playlist name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (playlistName.isNotBlank()) {
                            songActionsViewModel.createPlaylistAndAddSong(playlistName, selectedSong!!, context)
                            showCreatePlaylist = false
                        }
                    },
                    enabled = playlistName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylist = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            Column {
                // Network status banner
                AnimatedVisibility(
                    visible = uiState.showNetworkMessage,
                    enter = slideInVertically(),
                    exit = slideOutVertically()
                ) {
                    NetworkStatusBanner(
                        networkStatus = uiState.networkStatus,
                        wasOffline = uiState.wasOffline
                    )
                }
                
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = uiState.greeting,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Fresh picks tuned to your listening",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
                            )
                        }
                    },
                    actions = {
                        FilledTonalIconButton(onClick = { viewModel.loadHome(forceRefresh = true) }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh recommendations"
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalIconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.06f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            when {
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Collecting your next wave of tracks...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f)
                        )
                    }
                }
                uiState.error != null -> {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f),
                        tonalElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (uiState.networkStatus == NetworkStatus.Unavailable ||
                                uiState.networkStatus == NetworkStatus.Lost) {
                                Icon(
                                    imageVector = Icons.Default.SignalWifiOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(58.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                            Text(
                                text = uiState.error ?: "An error occurred",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            FilledTonalButton(onClick = { viewModel.loadHome(forceRefresh = true) }) {
                                Text("Try Again")
                            }
                        }
                    }
                }
                uiState.quickPicks.isEmpty() && uiState.personalizedRecommendations.isEmpty() &&
                uiState.recentlyPlayed.isEmpty() && uiState.trendingSongs.isEmpty() &&
                uiState.newReleases.isEmpty() && uiState.recommendedPlaylists.isEmpty() -> {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f),
                        tonalElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No content available",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Refresh and we will fetch a new mix for you",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            FilledTonalButton(onClick = { viewModel.loadHome(forceRefresh = true) }) {
                                Text("Refresh")
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        if (!uiState.motivationMessage.isNullOrBlank() || uiState.topArtists.isNotEmpty()) {
                            item {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    shape = RoundedCornerShape(22.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                    tonalElevation = 2.dp,
                                    shadowElevation = 1.dp
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = uiState.motivationMessage ?: "Music picked around your taste",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        if (uiState.topArtists.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Top artists: ${uiState.topArtists.joinToString(separator = " • ")}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (uiState.personalizedRecommendations.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Made For You")
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(uiState.personalizedRecommendations.take(20)) { song ->
                                        SongCard(
                                            song = song,
                                            onClick = {
                                                playSong(song, uiState.personalizedRecommendations, playerConnection)
                                            },
                                            onLongClick = {
                                                selectedSong = song
                                                showSongOptions = true
                                            }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }

                        // Quick Picks
                        if (uiState.quickPicks.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Quick Picks",
                                    onViewAll = { /* Navigate to full list */ }
                                )
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(uiState.quickPicks) { song ->
                                        SongCard(
                                            song = song,
                                            onClick = {
                                                songActionsViewModel.addToPlayHistory(song)
                                                playSong(song, uiState.quickPicks, playerConnection)
                                            },
                                            onLongClick = {
                                                selectedSong = song
                                                showSongOptions = true
                                            }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }

                        // Recently Played
                        if (uiState.recentlyPlayed.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Recently Played")
                            }
                            items(uiState.recentlyPlayed.take(8)) { song ->
                                SongListItem(
                                    song = song,
                                    onClick = {
                                        playSong(song, uiState.recentlyPlayed, playerConnection)
                                    },
                                    onMoreClick = {
                                        selectedSong = song
                                        showSongOptions = true
                                    }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                        
                        // Trending Songs
                        if (uiState.trendingSongs.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Trending Now")
                            }
                            items(uiState.trendingSongs.take(5)) { song ->
                                SongListItem(
                                    song = song,
                                    onClick = {
                                        songActionsViewModel.addToPlayHistory(song)
                                        playSong(song, uiState.trendingSongs, playerConnection)
                                    },
                                    onMoreClick = {
                                        selectedSong = song
                                        showSongOptions = true
                                    }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                        
                        // New Releases
                        if (uiState.newReleases.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "New Releases",
                                    onViewAll = { /* Navigate */ }
                                )
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(uiState.newReleases) { album ->
                                        AlbumCard(
                                            album = album,
                                            onClick = {
                                                navController.navigate(Screen.Album.createRoute(album.id))
                                            }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                        
                        // Recommended Playlists
                        if (uiState.recommendedPlaylists.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Playlists for You",
                                    onViewAll = { /* Navigate */ }
                                )
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(uiState.recommendedPlaylists) { playlist ->
                                        PlaylistCard(
                                            playlist = playlist,
                                            onClick = {
                                                navController.navigate(Screen.Playlist.createRoute(playlist.id))
                                            }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(100.dp)) // Bottom padding for mini player
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkStatusBanner(
    networkStatus: NetworkStatus,
    wasOffline: Boolean
) {
    val isOnline = networkStatus == NetworkStatus.Available
    val containerColor = if (isOnline && wasOffline) {
        Color(0xFF1C8E44)
    } else if (!isOnline) {
        Color(0xFFB02A2A)
    } else {
        return
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        color = containerColor,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isOnline) Icons.Default.Wifi else Icons.Default.SignalWifiOff,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isOnline && wasOffline) "Back online" else "No internet connection",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    onViewAll: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f) // Fixes overlapping text
        )
        onViewAll?.let {
            TextButton(
                onClick = it,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("View all")
            }
        }
    }
}

private fun playSong(
    song: SongItem,
    allSongs: List<SongItem>,
    playerConnection: com.beatloop.music.ui.PlayerConnection?
) {
    playerConnection?.let { connection ->
        val mediaItems = allSongs.map { s ->
            createMediaItem(
                id = s.id,
                title = s.title,
                artist = s.artistsText,
                thumbnailUrl = s.thumbnailUrl,
                localPath = s.localPath
            )
        }
        val startIndex = allSongs.indexOf(song).coerceAtLeast(0)
        connection.setMediaItems(mediaItems, startIndex)
    }
}
