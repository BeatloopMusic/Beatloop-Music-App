package com.beatloop.music.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.beatloop.music.data.model.SongItem
import com.beatloop.music.playback.createMediaItem
import com.beatloop.music.ui.LocalPlayerConnection
import com.beatloop.music.ui.components.AddToPlaylistBottomSheet
import com.beatloop.music.ui.components.AlbumCard
import com.beatloop.music.ui.components.SongListItem
import com.beatloop.music.ui.components.SongOptionsBottomSheet
import com.beatloop.music.ui.navigation.Screen
import com.beatloop.music.ui.viewmodel.ArtistViewModel
import com.beatloop.music.ui.viewmodel.SongActionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    artistId: String,
    navController: NavController,
    viewModel: ArtistViewModel = hiltViewModel(),
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
    
    LaunchedEffect(artistId) {
        viewModel.loadArtist(artistId)
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
                // Already on artist screen
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
                        Text(uiState.error ?: "Error loading artist")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadArtist(artistId) }) {
                            Text("Retry")
                        }
                    }
                }
            }
            uiState.artist != null -> {
                val artist = uiState.artist!!
                val configuration = LocalConfiguration.current
                val isCompactWidth = configuration.screenWidthDp < 360
                val isCompactHeight = configuration.screenHeightDp < 700
                val headerHeight = if (isCompactHeight) 236.dp else 280.dp
                val artistImageSize = if (isCompactHeight) 140.dp else 180.dp
                val bottomListPadding = if (isCompactHeight) 132.dp else 148.dp

                val playTopSongs: () -> Unit = {
                    playerConnection?.let { conn ->
                        val mediaItems = uiState.topSongs.map { song ->
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

                val shuffleTopSongs: () -> Unit = {
                    playerConnection?.let { conn ->
                        val mediaItems = uiState.topSongs.shuffled().map { song ->
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
                    // Header with Artist Image
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(headerHeight),
                            contentAlignment = Alignment.Center
                        ) {
                            // Background gradient
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                                MaterialTheme.colorScheme.background
                                            )
                                        )
                                    )
                            )
                            
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Artist Image
                                AsyncImage(
                                    model = artist.thumbnailUrl,
                                    contentDescription = "Artist Image",
                                    modifier = Modifier
                                        .size(artistImageSize)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Artist Name
                                Text(
                                    text = artist.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                
                                // Subscribers/Monthly Listeners
                                artist.subscribersText?.let { subscribers ->
                                    Text(
                                        text = subscribers,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    
                    // Action Buttons
                    item {
                        if (isCompactWidth) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = playTopSongs,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Play")
                                }

                                OutlinedButton(
                                    onClick = shuffleTopSongs,
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
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = playTopSongs,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Play")
                                }

                                OutlinedButton(
                                    onClick = shuffleTopSongs,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Shuffle, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Shuffle")
                                }
                            }
                        }
                    }
                    
                    // Top Songs Section
                    if (uiState.topSongs.isNotEmpty()) {
                        item {
                            Text(
                                text = "Popular",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                        
                        items(uiState.topSongs.take(5)) { song ->
                            SongListItem(
                                song = song,
                                onClick = {
                                    songActionsViewModel.addToPlayHistory(song)
                                    playerConnection?.let { conn ->
                                        val mediaItems = uiState.topSongs.map { s ->
                                            createMediaItem(
                                                id = s.id,
                                                title = s.title,
                                                artist = s.artistsText,
                                                thumbnailUrl = s.thumbnailUrl,
                                                localPath = s.localPath
                                            )
                                        }
                                        val startIndex = uiState.topSongs.indexOf(song)
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
                    
                    // Albums Section
                    if (uiState.albums.isNotEmpty()) {
                        item {
                            Text(
                                text = "Albums",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                        
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.albums) { album ->
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
                    
                    // Singles Section
                    if (uiState.singles.isNotEmpty()) {
                        item {
                            Text(
                                text = "Singles & EPs",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                        
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.singles) { album ->
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
                    
                    // Description
                    uiState.description?.let { description ->
                        if (description.isNotBlank()) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "About",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
