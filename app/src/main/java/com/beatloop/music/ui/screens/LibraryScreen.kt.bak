package com.beatloop.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.beatloop.music.playback.createMediaItem
import com.beatloop.music.ui.LocalPlayerConnection
import com.beatloop.music.ui.components.SongListItem
import com.beatloop.music.ui.navigation.Screen
import com.beatloop.music.ui.viewmodel.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playerConnection = LocalPlayerConnection.current
    
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Playlists", "Downloads", "History", "Liked")
    
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            
            when (selectedTab) {
                0 -> PlaylistsTab(
                    playlists = uiState.playlists,
                    onPlaylistClick = { playlist ->
                        navController.navigate(Screen.LocalPlaylist.createRoute(playlist.id))
                    },
                    onCreatePlaylist = { showCreatePlaylistDialog = true },
                    onDeletePlaylist = { viewModel.deletePlaylist(it) }
                )
                1 -> DownloadsTab(
                    downloads = uiState.downloads,
                    onSongClick = { song ->
                        playerConnection?.let { conn ->
                            val mediaItem = createMediaItem(
                                id = song.id,
                                title = song.title,
                                artist = song.artistsText,
                                thumbnailUrl = song.thumbnailUrl,
                                localPath = song.localPath
                            )
                            conn.setMediaItem(mediaItem)
                        }
                    },
                    onDeleteDownload = { viewModel.deleteDownload(it) }
                )
                2 -> HistoryTab(
                    history = uiState.playHistory,
                    onSongClick = { song ->
                        playerConnection?.let { conn ->
                            val mediaItem = createMediaItem(
                                id = song.id,
                                title = song.title,
                                artist = song.artistsText,
                                thumbnailUrl = song.thumbnailUrl,
                                localPath = song.localPath
                            )
                            conn.setMediaItem(mediaItem)
                        }
                    },
                    onClearHistory = { viewModel.clearHistory() }
                )
                3 -> LikedSongsTab(
                    likedSongs = uiState.likedSongs,
                    onSongClick = { song ->
                        playerConnection?.let { conn ->
                            val mediaItem = createMediaItem(
                                id = song.id,
                                title = song.title,
                                artist = song.artistsText,
                                thumbnailUrl = song.thumbnailUrl,
                                localPath = song.localPath
                            )
                            conn.setMediaItem(mediaItem)
                        }
                    },
                    onUnlike = { viewModel.unlikeSong(it) }
                )
            }
        }
    }
    
    // Create Playlist Dialog
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Create Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName)
                            newPlaylistName = ""
                            showCreatePlaylistDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PlaylistsTab(
    playlists: List<com.beatloop.music.data.model.LocalPlaylist>,
    onPlaylistClick: (com.beatloop.music.data.model.LocalPlaylist) -> Unit,
    onCreatePlaylist: () -> Unit,
    onDeletePlaylist: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 0.dp)
    ) {
        // Create Playlist Button
        item {
            ListItem(
                headlineContent = { Text("Create new playlist") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .padding(12.dp)
                    )
                },
                modifier = Modifier.clickable { onCreatePlaylist() }
            )
            HorizontalDivider()
        }
        
        items(playlists) { playlist ->
            var showMenu by remember { mutableStateOf(false) }
            
            ListItem(
                headlineContent = { Text(playlist.name) },
                supportingContent = { Text("${playlist.songCount} songs") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .padding(12.dp)
                    )
                },
                trailingContent = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    onDeletePlaylist(playlist.id)
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Delete, contentDescription = null)
                                }
                            )
                        }
                    }
                },
                modifier = Modifier.clickable { onPlaylistClick(playlist) }
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun DownloadsTab(
    downloads: List<com.beatloop.music.data.model.SongItem>,
    onSongClick: (com.beatloop.music.data.model.SongItem) -> Unit,
    onDeleteDownload: (String) -> Unit
) {
    if (downloads.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Download,
            title = "No downloads",
            message = "Downloaded songs will appear here"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(downloads) { song ->
                SongListItem(
                    song = song,
                    onClick = { onSongClick(song) },
                    onMoreClick = { onDeleteDownload(song.id) }
                )
            }
            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
private fun HistoryTab(
    history: List<com.beatloop.music.data.model.SongItem>,
    onSongClick: (com.beatloop.music.data.model.SongItem) -> Unit,
    onClearHistory: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (history.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onClearHistory) {
                    Text("Clear history")
                }
            }
        }
        
        if (history.isEmpty()) {
            EmptyState(
                icon = Icons.Default.History,
                title = "No history",
                message = "Songs you play will appear here"
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(history) { song ->
                    SongListItem(
                        song = song,
                        onClick = { onSongClick(song) },
                        onMoreClick = { /* Show options */ }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }
}

@Composable
private fun LikedSongsTab(
    likedSongs: List<com.beatloop.music.data.model.SongItem>,
    onSongClick: (com.beatloop.music.data.model.SongItem) -> Unit,
    onUnlike: (String) -> Unit
) {
    if (likedSongs.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Favorite,
            title = "No liked songs",
            message = "Songs you like will appear here"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(likedSongs) { song ->
                SongListItem(
                    song = song,
                    onClick = { onSongClick(song) },
                    onMoreClick = { onUnlike(song.id) }
                )
            }
            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
