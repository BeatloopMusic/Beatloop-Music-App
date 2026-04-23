package com.beatloop.music.ui.screens

import com.beatloop.music.ui.navigation.Screen

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.beatloop.music.data.model.SongItem
import com.beatloop.music.playback.createMediaItem
import com.beatloop.music.ui.LocalPlayerConnection
import com.beatloop.music.ui.components.AddToPlaylistBottomSheet
import com.beatloop.music.ui.components.SongListItem
import com.beatloop.music.ui.components.SongOptionsBottomSheet
import com.beatloop.music.ui.viewmodel.AlbumViewModel
import com.beatloop.music.ui.viewmodel.SongActionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    albumId: String,
    navController: NavController,
    viewModel: AlbumViewModel = hiltViewModel(),
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
    var newPlaylistName by remember { mutableStateOf("") }
    
    val playlists by songActionsViewModel.playlists.collectAsState()
    val likedSongIds by songActionsViewModel.likedSongIds.collectAsState()
    val downloadedSongIds by songActionsViewModel.downloadedSongIds.collectAsState()
    val downloadUiStateMap by songActionsViewModel.downloadUiStateMap.collectAsState()
    
    LaunchedEffect(albumId) {
        viewModel.loadAlbum(albumId)
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
                playerConnection?.let { conn ->
                    val mediaItem = createMediaItem(
                        id = selectedSong!!.id,
                        title = selectedSong!!.title,
                        artist = selectedSong!!.artistsText,
                        thumbnailUrl = selectedSong!!.thumbnailUrl,
                        localPath = selectedSong!!.localPath
                    )
                    conn.addMediaItemNext(mediaItem)
                }
                showSongOptions = false
            },
            onAddToQueue = {
                playerConnection?.let { conn ->
                    val mediaItem = createMediaItem(
                        id = selectedSong!!.id,
                        title = selectedSong!!.title,
                        artist = selectedSong!!.artistsText,
                        thumbnailUrl = selectedSong!!.thumbnailUrl,
                        localPath = selectedSong!!.localPath
                    )
                    conn.addMediaItem(mediaItem)
                }
                showSongOptions = false
            },
            onAddToPlaylist = {
                showSongOptions = false
                showAddToPlaylist = true
            },
            onLike = {
                selectedSong?.let { song ->
                    songActionsViewModel.toggleLike(song)
                }
                showSongOptions = false
            },
            onDownload = {
                selectedSong?.let(songActionsViewModel::downloadSong)
                navController.navigate(Screen.Downloads.route)
                showSongOptions = false
            },
            onShare = {
                selectedSong?.let { song ->
                    val shareIntent = songActionsViewModel.createShareIntent(song)
                    context.startActivity(Intent.createChooser(shareIntent, "Share song"))
                }
                showSongOptions = false
            },
            onGoToArtist = {
                selectedSong?.artistId?.let { artistId ->
                    navController.navigate(Screen.Artist.createRoute(artistId))
                }
                showSongOptions = false
            },
            onGoToAlbum = {
                // Already on album screen
                showSongOptions = false
            }
        )
    }
    
    // Add to Playlist Bottom Sheet
    if (showAddToPlaylist && selectedSong != null) {
        AddToPlaylistBottomSheet(
            playlists = playlists,
            onDismiss = { showAddToPlaylist = false },
            onSelectPlaylist = { playlistId ->
                selectedSong?.let { song ->
                    songActionsViewModel.addToPlaylist(playlistId, song, context)
                }
                showAddToPlaylist = false
            },
            onCreateNew = {
                showAddToPlaylist = false
                showCreatePlaylist = true
            }
        )
    }
    
    // Create Playlist Dialog
    if (showCreatePlaylist) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false },
            title = { Text("Create Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            selectedSong?.let { song ->
                                songActionsViewModel.createPlaylistAndAddSong(newPlaylistName, song, context)
                            }
                            newPlaylistName = ""
                            showCreatePlaylist = false
                        }
                    }
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
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(uiState.error ?: "Error loading album")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadAlbum(albumId) }) {
                            Text("Retry")
                        }
                    }
                }
            }
            uiState.album != null -> {
                val album = uiState.album!!
                val configuration = LocalConfiguration.current
                val isCompactWidth = configuration.screenWidthDp < 360
                val isCompactHeight = configuration.screenHeightDp < 700
                val headerHeight = if (isCompactHeight) 220.dp else 300.dp
                val bottomListPadding = if (isCompactHeight) 132.dp else 148.dp

                val playAllSongs: () -> Unit = {
                    playerConnection?.let { conn ->
                        val mediaItems = uiState.songs.map { song ->
                            createMediaItem(
                                id = song.id,
                                title = song.title,
                                artist = song.artistsText,
                                thumbnailUrl = song.thumbnailUrl,
                                localPath = song.localPath
                            )
                        }
                        conn.setMediaItems(mediaItems, 0)
                    }
                    Unit
                }

                val shuffleSongs: () -> Unit = {
                    playerConnection?.let { conn ->
                        val mediaItems = uiState.songs.shuffled().map { song ->
                            createMediaItem(
                                id = song.id,
                                title = song.title,
                                artist = song.artistsText,
                                thumbnailUrl = song.thumbnailUrl,
                                localPath = song.localPath
                            )
                        }
                        conn.setMediaItems(mediaItems, 0)
                    }
                    Unit
                }
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = bottomListPadding)
                ) {
                    // Header with Album Art
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(headerHeight)
                        ) {
                            AsyncImage(
                                model = album.thumbnailUrl,
                                contentDescription = "Album Cover",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.background
                                            )
                                        )
                                    )
                            )
                        }
                    }
                    
                    // Album Info
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = album.title,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = album.artistsText,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            album.year?.let { year ->
                                Text(
                                    text = year.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            // Action Buttons
                            if (isCompactWidth) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = playAllSongs,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Play")
                                    }
                                    OutlinedButton(
                                        onClick = shuffleSongs,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Shuffle, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Shuffle")
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = playAllSongs,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Play")
                                    }

                                    OutlinedButton(
                                        onClick = shuffleSongs,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Shuffle, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Shuffle")
                                    }
                                }
                            }
                        }
                    }
                    
                    // Song Count
                    item {
                        Text(
                            text = "${uiState.songs.size} songs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    
                    // Songs
                    items(uiState.songs) { song ->
                        SongListItem(
                            song = song,
                            onClick = {
                                songActionsViewModel.addToPlayHistory(song)
                                playerConnection?.let { conn ->
                                    val mediaItems = uiState.songs.map { s ->
                                        createMediaItem(
                                            id = s.id,
                                            title = s.title,
                                            artist = s.artistsText,
                                            thumbnailUrl = s.thumbnailUrl,
                                            localPath = s.localPath
                                        )
                                    }
                                    val startIndex = uiState.songs.indexOf(song)
                                    conn.setMediaItems(mediaItems, startIndex)
                                }
                            },
                            onMoreClick = {
                                selectedSong = song
                                showSongOptions = true
                            }
                        )
                    }
                    
                }
            }
        }
    }
}

