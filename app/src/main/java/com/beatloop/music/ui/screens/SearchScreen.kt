package com.beatloop.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.beatloop.music.data.model.SongItem
import com.beatloop.music.playback.createMediaItem
import com.beatloop.music.ui.LocalPlayerConnection
import com.beatloop.music.ui.components.*
import com.beatloop.music.ui.navigation.Screen
import com.beatloop.music.ui.viewmodel.SearchViewModel
import com.beatloop.music.ui.viewmodel.SongActionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    viewModel: SearchViewModel = hiltViewModel(),
    songActionsViewModel: SongActionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val playerConnection = LocalPlayerConnection.current
    val context = LocalContext.current
    
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    
    // Song options state
    var selectedSong by remember { mutableStateOf<SongItem?>(null) }
    var showSongOptions by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    
    val playlists by songActionsViewModel.playlists.collectAsState()
    val likedSongIds by songActionsViewModel.likedSongIds.collectAsState()
    
    // Song Options Bottom Sheet
    if (showSongOptions && selectedSong != null) {
        SongOptionsBottomSheet(
            song = selectedSong!!,
            isLiked = likedSongIds.contains(selectedSong!!.id),
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
            onLike = { songActionsViewModel.toggleLike(selectedSong!!) },
            onAddToPlaylist = {
                showSongOptions = false
                showAddToPlaylist = true
            },
            onDownload = {
                selectedSong?.let(songActionsViewModel::downloadSong)
                showSongOptions = false
            },
            onGoToArtist = { /* TODO: Navigate to artist */ },
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
                songActionsViewModel.addToPlaylist(playlistId, selectedSong!!)
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
                            songActionsViewModel.createPlaylistAndAddSong(playlistName, selectedSong!!)
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
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        SearchBar(
            query = searchQuery,
            onQueryChange = { query ->
                searchQuery = query
                if (query.length >= 2) {
                    viewModel.getSearchSuggestions(query)
                }
            },
            onSearch = { query ->
                if (query.isNotBlank()) {
                    viewModel.search(query)
                    focusManager.clearFocus()
                    isSearchActive = false
                }
            },
            active = isSearchActive,
            onActiveChange = { isSearchActive = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isSearchActive) 0.dp else 16.dp)
                .focusRequester(focusRequester),
            placeholder = { Text("Search songs, artists, albums...") },
            leadingIcon = {
                if (isSearchActive) {
                    IconButton(onClick = { isSearchActive = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                } else {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            }
        ) {
            // Search suggestions and history
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                // Suggestions
                if (uiState.suggestions.isNotEmpty()) {
                    items(uiState.suggestions) { suggestion ->
                        ListItem(
                            headlineContent = { Text(suggestion) },
                            leadingContent = {
                                Icon(Icons.Default.Search, contentDescription = null)
                            },
                            modifier = Modifier.clickable {
                                searchQuery = suggestion
                                viewModel.search(suggestion)
                                focusManager.clearFocus()
                                isSearchActive = false
                            }
                        )
                    }
                }
                
                // Search History
                if (uiState.suggestions.isEmpty() && searchHistory.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent searches",
                                style = MaterialTheme.typography.titleMedium
                            )
                            TextButton(onClick = { viewModel.clearSearchHistory() }) {
                                Text("Clear all")
                            }
                        }
                    }
                    items(searchHistory) { historyItem ->
                        ListItem(
                            headlineContent = { Text(historyItem.query) },
                            leadingContent = {
                                Icon(Icons.Default.History, contentDescription = null)
                            },
                            trailingContent = {
                                IconButton(onClick = { viewModel.deleteSearchHistory(historyItem.query) }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Remove")
                                }
                            },
                            modifier = Modifier.clickable {
                                searchQuery = historyItem.query
                                viewModel.search(historyItem.query)
                                focusManager.clearFocus()
                                isSearchActive = false
                            }
                        )
                    }
                }
            }
        }
        
        // Search Results
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
                        Text(
                            text = uiState.error ?: "Error",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.search(searchQuery) }) {
                            Text("Retry")
                        }
                    }
                }
            }
            uiState.hasSearched -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    // Songs
                    if (uiState.songs.isNotEmpty()) {
                        item {
                            SectionTitle(title = "Songs")
                        }
                        items(uiState.songs.take(10)) { song ->
                            SongListItem(
                                song = song,
                                onClick = {
                                    songActionsViewModel.addToPlayHistory(song)
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
                                onMoreClick = {
                                    selectedSong = song
                                    showSongOptions = true
                                }
                            )
                        }
                    }
                    
                    // Artists
                    if (uiState.artists.isNotEmpty()) {
                        item {
                            SectionTitle(title = "Artists")
                        }
                        items(uiState.artists.take(10)) { artist ->
                            ArtistListItem(
                                artist = artist,
                                onClick = {
                                    navController.navigate(Screen.Artist.createRoute(artist.id))
                                }
                            )
                        }
                    }
                    
                    // Albums
                    if (uiState.albums.isNotEmpty()) {
                        item {
                            SectionTitle(title = "Albums")
                        }
                        items(uiState.albums.take(10)) { album ->
                            ListItem(
                                headlineContent = { Text(album.title) },
                                supportingContent = { Text(album.artistsText) },
                                leadingContent = {
                                    coil.compose.AsyncImage(
                                        model = album.thumbnailUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .padding(4.dp)
                                    )
                                },
                                modifier = Modifier.clickable {
                                    navController.navigate(Screen.Album.createRoute(album.id))
                                }
                            )
                        }
                    }
                    
                    // Playlists
                    if (uiState.playlists.isNotEmpty()) {
                        item {
                            SectionTitle(title = "Playlists")
                        }
                        items(uiState.playlists.take(10)) { playlist ->
                            ListItem(
                                headlineContent = { Text(playlist.title) },
                                supportingContent = { playlist.author?.let { Text(it) } },
                                leadingContent = {
                                    coil.compose.AsyncImage(
                                        model = playlist.thumbnailUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .padding(4.dp)
                                    )
                                },
                                modifier = Modifier.clickable {
                                    navController.navigate(Screen.Playlist.createRoute(playlist.id))
                                }
                            )
                        }
                    }
                    
                    // No results
                    if (uiState.songs.isEmpty() && uiState.artists.isEmpty() && 
                        uiState.albums.isEmpty() && uiState.playlists.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No results found",
                                    style = MaterialTheme.typography.bodyLarge,
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

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        color = MaterialTheme.colorScheme.onBackground
    )
}
