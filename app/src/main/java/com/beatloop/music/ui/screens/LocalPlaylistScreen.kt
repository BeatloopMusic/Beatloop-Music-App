package com.beatloop.music.ui.screens

import com.beatloop.music.ui.navigation.Screen

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.beatloop.music.data.model.SongItem
import com.beatloop.music.playback.createMediaItem
import com.beatloop.music.ui.LocalPlayerConnection
import com.beatloop.music.ui.components.AddToPlaylistBottomSheet
import com.beatloop.music.ui.components.SongListItem
import com.beatloop.music.ui.components.SongOptionsBottomSheet
import com.beatloop.music.ui.viewmodel.LocalPlaylistViewModel
import com.beatloop.music.ui.viewmodel.SongActionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistScreen(
    playlistId: Long,
    navController: NavController,
    viewModel: LocalPlaylistViewModel = hiltViewModel(),
    songActionsViewModel: SongActionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playerConnection = LocalPlayerConnection.current
    val context = LocalContext.current
    
    var showRenameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    
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
    
    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.playlist?.name ?: "Playlist") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Rename
                    IconButton(onClick = { 
                        newName = uiState.playlist?.name ?: ""
                        showRenameDialog = true 
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename")
                    }
                    // Delete
                    IconButton(onClick = {
                        viewModel.deletePlaylist()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
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
            uiState.playlist == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Playlist not found")
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Playlist Info
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = uiState.playlist!!.name,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${uiState.songs.size} songs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // Action Buttons
                        if (uiState.songs.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Play Button
                                Button(
                                    onClick = {
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
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Play")
                                }
                                
                                // Shuffle Button
                                OutlinedButton(
                                    onClick = {
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
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Shuffle, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Shuffle")
                                }
                            }
                        }
                    }
                    
                    // Songs
                    if (uiState.songs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No songs yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Add songs to this playlist",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                            items(uiState.songs) { song ->
                                SongListItem(
                                    song = song,
                                    onClick = {
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
    }
    
    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.renamePlaylist(newName)
                            showRenameDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
            onRemoveFromPlaylist = {
                viewModel.removeSongFromPlaylist(selectedSong!!.id)
                showSongOptions = false
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
                selectedSong?.albumId?.let { albumId ->
                    navController.navigate("album/$albumId")
                }
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
                TextButton(
                    onClick = {
                        newPlaylistName = ""
                        showCreatePlaylist = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

