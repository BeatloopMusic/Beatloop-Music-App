package com.beatloop.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.beatloop.music.ui.PlayerConnection
import com.beatloop.music.ui.components.AddToPlaylistBottomSheet
import com.beatloop.music.ui.components.AlbumCard
import com.beatloop.music.ui.components.PlaylistCard
import com.beatloop.music.ui.components.PremiumEmptyState
import com.beatloop.music.ui.components.PremiumErrorState
import com.beatloop.music.ui.components.PremiumHeroCard
import com.beatloop.music.ui.components.PremiumOfflineBanner
import com.beatloop.music.ui.components.PremiumScreenBackground
import com.beatloop.music.ui.components.PremiumSectionHeader
import com.beatloop.music.ui.components.PremiumSkeletonCard
import com.beatloop.music.ui.components.PremiumSkeletonHero
import com.beatloop.music.ui.components.PremiumSkeletonListItem
import com.beatloop.music.ui.components.SongCard
import com.beatloop.music.ui.components.SongListItem
import com.beatloop.music.ui.components.SongOptionsBottomSheet
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
                            songActionsViewModel.createPlaylistAndAddSong(
                                playlistName,
                                selectedSong!!,
                                context
                            )
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
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            Column {
                AnimatedVisibility(
                    visible = uiState.showNetworkMessage,
                    enter = slideInVertically(),
                    exit = slideOutVertically()
                ) {
                    HomeNetworkBanner(
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
                                text = "Editorial mixes shaped by your sessions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        FilledTonalIconButton(onClick = { viewModel.loadHome(forceRefresh = true) }) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh recommendations"
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalIconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )
            }
        }
    ) { padding ->
        PremiumScreenBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    HomeLoadingState()
                }

                uiState.error != null -> {
                    PremiumErrorState(
                        message = uiState.error ?: "Unable to load your feed.",
                        onRetry = { viewModel.loadHome(forceRefresh = true) }
                    )
                }

                uiState.quickPicks.isEmpty() &&
                    uiState.personalizedRecommendations.isEmpty() &&
                    uiState.recentlyPlayed.isEmpty() &&
                    uiState.trendingSongs.isEmpty() &&
                    uiState.genreSections.isEmpty() &&
                    uiState.newReleases.isEmpty() &&
                    uiState.recommendedPlaylists.isEmpty() -> {
                    PremiumEmptyState(
                        title = "Your home feed is quiet",
                        message = "Refresh to pull a fresh editorial mix.",
                        icon = Icons.Default.SignalWifiOff,
                        actionLabel = "Refresh",
                        onAction = { viewModel.loadHome(forceRefresh = true) }
                    )
                }

                else -> {
                    val continueListening = uiState.quickPicks.ifEmpty { uiState.recentlyPlayed }
                    val becauseYouPlayed =
                        (uiState.quickPicks + uiState.personalizedRecommendations)
                            .distinctBy { it.id }
                            .take(20)

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        if (uiState.personalizedRecommendations.isNotEmpty()) {
                            val heroSong = uiState.personalizedRecommendations.first()
                            item {
                                PremiumHeroCard(
                                    title = heroSong.title,
                                    subtitle = "${heroSong.artistsText} • curated from your recent mood",
                                    badge = "Made For You",
                                    onClick = {
                                        playSong(
                                            song = heroSong,
                                            allSongs = uiState.personalizedRecommendations,
                                            playerConnection = playerConnection
                                        )
                                    },
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }

                        if (continueListening.isNotEmpty()) {
                            item {
                                PremiumSectionHeader(
                                    title = "Continue Listening",
                                    subtitle = "Jump back in instantly"
                                )
                            }
                            item {
                                SongCarouselSection(
                                    songs = continueListening.take(20),
                                    onSongClick = { song ->
                                        playSong(song, continueListening, playerConnection)
                                    },
                                    onSongLongClick = { song ->
                                        selectedSong = song
                                        showSongOptions = true
                                    }
                                )
                            }
                        }

                        if (uiState.personalizedRecommendations.isNotEmpty()) {
                            item {
                                PremiumSectionHeader(
                                    title = "Made For You",
                                    subtitle = "Fresh recommendations tuned to your profile"
                                )
                            }
                            item {
                                SongCarouselSection(
                                    songs = uiState.personalizedRecommendations.take(20),
                                    onSongClick = { song ->
                                        playSong(
                                            song,
                                            uiState.personalizedRecommendations,
                                            playerConnection
                                        )
                                    },
                                    onSongLongClick = { song ->
                                        selectedSong = song
                                        showSongOptions = true
                                    }
                                )
                            }
                        }

                        if (becauseYouPlayed.isNotEmpty()) {
                            item {
                                PremiumSectionHeader(
                                    title = "Because You Played",
                                    subtitle = "Blended from your strongest repeats"
                                )
                            }
                            item {
                                SongCarouselSection(
                                    songs = becauseYouPlayed,
                                    onSongClick = { song ->
                                        playSong(song, becauseYouPlayed, playerConnection)
                                    },
                                    onSongLongClick = { song ->
                                        selectedSong = song
                                        showSongOptions = true
                                    }
                                )
                            }
                        }

                        uiState.genreSections
                            .filter { section -> section.songs.isNotEmpty() }
                            .forEach { section ->
                                item {
                                    PremiumSectionHeader(
                                        title = section.title,
                                        subtitle = "Personalized genre picks for your current language preference"
                                    )
                                }
                                item {
                                    SongCarouselSection(
                                        songs = section.songs.take(20),
                                        onSongClick = { song ->
                                            playSong(song, section.songs, playerConnection)
                                        },
                                        onSongLongClick = { song ->
                                            selectedSong = song
                                            showSongOptions = true
                                        }
                                    )
                                }
                            }

                        if (uiState.recentlyPlayed.isNotEmpty()) {
                            item {
                                PremiumSectionHeader(
                                    title = "Recently Played",
                                    subtitle = "Your latest listening trail"
                                )
                            }
                            items(uiState.recentlyPlayed.take(8), key = { it.id }) { song ->
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
                        }

                        if (uiState.trendingSongs.isNotEmpty()) {
                            item {
                                PremiumSectionHeader(
                                    title = "Trending Now",
                                    subtitle = "What listeners are looping globally"
                                )
                            }
                            items(uiState.trendingSongs.take(6), key = { it.id }) { song ->
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
                        }

                        if (uiState.newReleases.isNotEmpty()) {
                            item {
                                PremiumSectionHeader(
                                    title = "New Releases",
                                    subtitle = "Recently dropped albums and sessions"
                                )
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(uiState.newReleases, key = { it.id }) { album ->
                                        AlbumCard(
                                            album = album,
                                            onClick = {
                                                navController.navigate(Screen.Album.createRoute(album.id))
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (uiState.recommendedPlaylists.isNotEmpty()) {
                            item {
                                PremiumSectionHeader(
                                    title = "Playlists For You",
                                    subtitle = "High-signal collections from your taste graph"
                                )
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(uiState.recommendedPlaylists, key = { it.id }) { playlist ->
                                        PlaylistCard(
                                            playlist = playlist,
                                            onClick = {
                                                navController.navigate(
                                                    Screen.Playlist.createRoute(playlist.id)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongCarouselSection(
    songs: List<SongItem>,
    onSongClick: (SongItem) -> Unit,
    onSongLongClick: (SongItem) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(songs, key = { it.id }) { song ->
            SongCard(
                song = song,
                onClick = { onSongClick(song) },
                onLongClick = { onSongLongClick(song) }
            )
        }
    }
}

@Composable
private fun HomeLoadingState() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            PremiumSkeletonHero(modifier = Modifier.padding(top = 10.dp))
        }
        item {
            PremiumSectionHeader(
                title = "Loading your feed",
                subtitle = "Analyzing your latest sessions"
            )
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(4) {
                    PremiumSkeletonCard()
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            PremiumSectionHeader(
                title = "Preparing tracks",
                subtitle = "One moment"
            )
        }
        items(5) {
            PremiumSkeletonListItem()
        }
    }
}

@Composable
private fun HomeNetworkBanner(
    networkStatus: NetworkStatus,
    wasOffline: Boolean
) {
    val isOnlineRecovery = networkStatus == NetworkStatus.Available && wasOffline
    val isOffline = networkStatus == NetworkStatus.Unavailable ||
        networkStatus == NetworkStatus.Lost

    if (!isOnlineRecovery && !isOffline) return

    PremiumOfflineBanner(isOnlineRecovery = isOnlineRecovery)
}

private fun playSong(
    song: SongItem,
    allSongs: List<SongItem>,
    playerConnection: PlayerConnection?
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
