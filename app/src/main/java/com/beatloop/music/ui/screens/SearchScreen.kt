package com.beatloop.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.beatloop.music.data.model.SearchFilter
import com.beatloop.music.data.model.SongItem
import com.beatloop.music.playback.createMediaItem
import com.beatloop.music.ui.LocalPlayerConnection
import com.beatloop.music.ui.components.AddToPlaylistBottomSheet
import com.beatloop.music.ui.components.ArtistListItem
import com.beatloop.music.ui.components.PremiumEmptyState
import com.beatloop.music.ui.components.PremiumErrorState
import com.beatloop.music.ui.components.PremiumFilterChipRow
import com.beatloop.music.ui.components.PremiumGlassSurface
import com.beatloop.music.ui.components.PremiumScreenBackground
import com.beatloop.music.ui.components.PremiumSectionHeader
import com.beatloop.music.ui.components.PremiumSkeletonListItem
import com.beatloop.music.ui.components.SongListItem
import com.beatloop.music.ui.components.SongOptionsBottomSheet
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
    var selectedFilterLabel by remember { mutableStateOf("All") }
    var isSearchFocused by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    var selectedSong by remember { mutableStateOf<SongItem?>(null) }
    var showSongOptions by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }

    val playlists by songActionsViewModel.playlists.collectAsState()
    val likedSongIds by songActionsViewModel.likedSongIds.collectAsState()
    val downloadedSongIds by songActionsViewModel.downloadedSongIds.collectAsState()
    val downloadUiStateMap by songActionsViewModel.downloadUiStateMap.collectAsState()

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
            onLike = { songActionsViewModel.toggleLike(selectedSong!!) },
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

    val filterLabels = remember {
        listOf("All", "Songs", "Artists", "Albums", "Playlists", "Videos")
    }

    fun submitSearch() {
        if (searchQuery.isBlank()) return
        viewModel.search(searchQuery.trim(), selectedFilterLabel.toSearchFilter())
        focusManager.clearFocus()
    }

    LaunchedEffect(selectedFilterLabel) {
        if (uiState.hasSearched && searchQuery.isNotBlank()) {
            viewModel.search(searchQuery.trim(), selectedFilterLabel.toSearchFilter())
        }
    }

    Scaffold(
        containerColor = Color.Transparent
    ) { padding ->
        PremiumScreenBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                PremiumGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    searchQuery = ""
                                    viewModel.clearSearch()
                                } else {
                                    focusManager.clearFocus()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isSearchFocused || searchQuery.isNotBlank()) {
                                    Icons.AutoMirrored.Filled.ArrowBack
                                } else {
                                    Icons.Default.Search
                                },
                                contentDescription = "Search"
                            )
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { query ->
                                searchQuery = query
                                if (query.length >= 2) {
                                    viewModel.getSearchSuggestions(query)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .onFocusChanged { isSearchFocused = it.isFocused },
                            placeholder = { Text("Search songs, artists, albums, playlists...") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { submitSearch() }
                            ),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            )
                        )

                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                }

                PremiumFilterChipRow(
                    items = filterLabels,
                    selectedItem = selectedFilterLabel,
                    onItemSelected = { selectedFilterLabel = it }
                )

                Spacer(modifier = Modifier.height(8.dp))

                when {
                    uiState.isLoading -> {
                        SearchLoadingState()
                    }

                    uiState.error != null -> {
                        PremiumErrorState(
                            message = uiState.error ?: "Search failed",
                            onRetry = { submitSearch() }
                        )
                    }

                    uiState.hasSearched -> {
                        val hasResults = uiState.songs.isNotEmpty() ||
                            uiState.artists.isNotEmpty() ||
                            uiState.albums.isNotEmpty() ||
                            uiState.playlists.isNotEmpty() ||
                            uiState.videos.isNotEmpty()

                        if (!hasResults) {
                            PremiumEmptyState(
                                title = "No matches found",
                                message = "Try a broader keyword or switch a filter.",
                                icon = Icons.Default.Search,
                                actionLabel = "Clear search",
                                onAction = {
                                    searchQuery = ""
                                    viewModel.clearSearch()
                                }
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 120.dp)
                            ) {
                                if (uiState.songs.isNotEmpty()) {
                                    item {
                                        PremiumSectionHeader(
                                            title = "Songs",
                                            subtitle = "Top audio matches"
                                        )
                                    }
                                    items(uiState.songs.take(12), key = { it.id }) { song ->
                                        SongListItem(
                                            song = song,
                                            onClick = {
                                                songActionsViewModel.addToPlayHistory(song)
                                                playerConnection?.let { conn ->
                                                    conn.setMediaItem(
                                                        createMediaItem(
                                                            id = song.id,
                                                            title = song.title,
                                                            artist = song.artistsText,
                                                            thumbnailUrl = song.thumbnailUrl,
                                                            localPath = song.localPath
                                                        )
                                                    )
                                                }
                                            },
                                            trailing = {
                                                IconButton(
                                                    onClick = {
                                                        selectedSong = song
                                                        showAddToPlaylist = true
                                                    },
                                                    modifier = Modifier.size(34.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                                        contentDescription = "Add to playlist",
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                            },
                                            onMoreClick = {
                                                selectedSong = song
                                                showSongOptions = true
                                            }
                                        )
                                    }
                                }

                                if (uiState.artists.isNotEmpty()) {
                                    item {
                                        PremiumSectionHeader(
                                            title = "Artists",
                                            subtitle = "Creators related to this query"
                                        )
                                    }
                                    items(uiState.artists.take(10), key = { it.id }) { artist ->
                                        ArtistListItem(
                                            artist = artist,
                                            onClick = {
                                                navController.navigate(
                                                    Screen.Artist.createRoute(artist.id)
                                                )
                                            }
                                        )
                                    }
                                }

                                if (uiState.albums.isNotEmpty()) {
                                    item {
                                        PremiumSectionHeader(
                                            title = "Albums",
                                            subtitle = "Long-form listening"
                                        )
                                    }
                                    items(uiState.albums.take(10), key = { it.id }) { album ->
                                        PremiumGlassSurface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                                .clickable {
                                                    navController.navigate(
                                                        Screen.Album.createRoute(album.id)
                                                    )
                                                }
                                        ) {
                                            ListItem(
                                                headlineContent = { Text(album.title) },
                                                supportingContent = { Text(album.artistsText) },
                                                leadingContent = {
                                                    AsyncImage(
                                                        model = album.thumbnailUrl,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(56.dp)
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }

                                if (uiState.playlists.isNotEmpty()) {
                                    item {
                                        PremiumSectionHeader(
                                            title = "Playlists",
                                            subtitle = "Collections and curated sets"
                                        )
                                    }
                                    items(uiState.playlists.take(10), key = { it.id }) { playlist ->
                                        PremiumGlassSurface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                                .clickable {
                                                    navController.navigate(
                                                        Screen.Playlist.createRoute(playlist.id)
                                                    )
                                                }
                                        ) {
                                            ListItem(
                                                headlineContent = { Text(playlist.title) },
                                                supportingContent = {
                                                    if (!playlist.author.isNullOrBlank()) {
                                                        Text(playlist.author)
                                                    }
                                                },
                                                leadingContent = {
                                                    AsyncImage(
                                                        model = playlist.thumbnailUrl,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(56.dp)
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        SuggestionHistoryPanel(
                            suggestions = uiState.suggestions,
                            history = searchHistory.map { it.query },
                            onSuggestionClick = { suggestion ->
                                searchQuery = suggestion
                                viewModel.search(suggestion, selectedFilterLabel.toSearchFilter())
                                focusManager.clearFocus()
                            },
                            onHistoryClick = { historyItem ->
                                searchQuery = historyItem
                                viewModel.search(historyItem, selectedFilterLabel.toSearchFilter())
                                focusManager.clearFocus()
                            },
                            onDeleteHistoryItem = { viewModel.deleteSearchHistory(it) },
                            onClearHistory = { viewModel.clearSearchHistory() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionHistoryPanel(
    suggestions: List<String>,
    history: List<String>,
    onSuggestionClick: (String) -> Unit,
    onHistoryClick: (String) -> Unit,
    onDeleteHistoryItem: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        if (suggestions.isNotEmpty()) {
            item {
                PremiumSectionHeader(
                    title = "Suggestions",
                    subtitle = "Fast jump to likely matches"
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(suggestions) { suggestion ->
                        AssistChip(
                            onClick = { onSuggestionClick(suggestion) },
                            label = { Text(suggestion) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                            )
                        )
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            item {
                PremiumSectionHeader(
                    title = "Recent Searches",
                    subtitle = "Your latest discovery paths",
                    actionLabel = "Clear all",
                    onAction = onClearHistory
                )
            }
            items(history, key = { it }) { historyItem ->
                PremiumGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { onHistoryClick(historyItem) }
                ) {
                    ListItem(
                        headlineContent = { Text(historyItem) },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { onDeleteHistoryItem(historyItem) }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Delete history item"
                                )
                            }
                        }
                    )
                }
            }
        }

        if (suggestions.isEmpty() && history.isEmpty()) {
            item {
                PremiumGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Start searching",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Look up songs, artists, albums, and playlists.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchLoadingState() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            PremiumSectionHeader(
                title = "Searching",
                subtitle = "Fetching the best matches"
            )
        }
        items(8) {
            PremiumSkeletonListItem()
        }
    }
}

private fun String.toSearchFilter(): SearchFilter {
    return when (this) {
        "Songs" -> SearchFilter.Songs
        "Artists" -> SearchFilter.Artists
        "Albums" -> SearchFilter.Albums
        "Playlists" -> SearchFilter.Playlists
        "Videos" -> SearchFilter.Videos
        else -> SearchFilter.All
    }
}
