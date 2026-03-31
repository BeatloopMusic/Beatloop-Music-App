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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.beatloop.music.data.model.LocalPlaylist
import com.beatloop.music.data.model.SongItem
import com.beatloop.music.playback.createMediaItem
import com.beatloop.music.ui.LocalPlayerConnection
import com.beatloop.music.ui.components.PremiumEmptyState
import com.beatloop.music.ui.components.PremiumGlassSurface
import com.beatloop.music.ui.components.PremiumHeroCard
import com.beatloop.music.ui.components.PremiumScreenBackground
import com.beatloop.music.ui.components.PremiumSectionHeader
import com.beatloop.music.ui.components.SongListItem
import com.beatloop.music.ui.navigation.Screen
import com.beatloop.music.ui.viewmodel.LibraryViewModel
import com.beatloop.music.ui.viewmodel.SongActionsViewModel
import kotlinx.coroutines.launch

private data class ArtistArchive(
    val id: String?,
    val name: String,
    val count: Int,
    val thumbnailUrl: String?
)

private data class AlbumArchive(
    val id: String,
    val title: String,
    val count: Int,
    val thumbnailUrl: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    viewModel: LibraryViewModel = hiltViewModel(),
    songActionsViewModel: SongActionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val downloadSizeMap by viewModel.downloadSizeMap.collectAsState()
    val activeDownloadStateMap by songActionsViewModel.downloadUiStateMap.collectAsState()
    val activeDownloadSongInfo by songActionsViewModel.activeDownloadSongInfo.collectAsState()
    val playerConnection = LocalPlayerConnection.current

    val tabs = listOf("Playlists", "Downloads", "History", "Likes", "Artists", "Albums")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    val archiveSongs = remember(uiState) {
        (uiState.likedSongs + uiState.playHistory + uiState.downloads).distinctBy { it.id }
    }

    val artistsArchive = remember(archiveSongs) {
        archiveSongs
            .groupBy { it.artistsText.ifBlank { "Unknown Artist" } }
            .map { (artistName, songs) ->
                ArtistArchive(
                    id = songs.firstOrNull()?.artistId,
                    name = artistName,
                    count = songs.size,
                    thumbnailUrl = songs.firstOrNull()?.thumbnailUrl
                )
            }
            .sortedByDescending { it.count }
    }

    val albumsArchive = remember(archiveSongs) {
        archiveSongs
            .filter { !it.albumId.isNullOrBlank() }
            .groupBy { it.albumId!! }
            .map { (albumId, songs) ->
                AlbumArchive(
                    id = albumId,
                    title = songs.firstOrNull()?.title ?: "Album $albumId",
                    count = songs.size,
                    thumbnailUrl = songs.firstOrNull()?.thumbnailUrl
                )
            }
            .sortedByDescending { it.count }
    }

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    androidx.compose.material3.Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Your Archive",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Pinned collections and listening history",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        }
    ) { padding ->
        PremiumScreenBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PremiumHeroCard(
                    title = "${archiveSongs.size} tracks across your library",
                    subtitle = "${uiState.playlists.size} playlists • ${uiState.downloads.size} downloads • ${uiState.likedSongs.size} liked",
                    badge = "Personal Archive",
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(0) }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        LibraryQuickActionCard(
                            title = "Likes",
                            value = uiState.likedSongs.size.toString(),
                            icon = Icons.Default.Favorite,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(3) }
                            }
                        )
                    }
                    item {
                        LibraryQuickActionCard(
                            title = "History",
                            value = uiState.playHistory.size.toString(),
                            icon = Icons.Default.History,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(2) }
                            }
                        )
                    }
                    item {
                        LibraryQuickActionCard(
                            title = "Downloads",
                            value = uiState.downloads.size.toString(),
                            icon = Icons.Default.Download,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(1) }
                            }
                        )
                    }
                    item {
                        LibraryQuickActionCard(
                            title = "Playlists",
                            value = uiState.playlists.size.toString(),
                            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(0) }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 16.dp,
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = { Text(title) }
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
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
                            downloadSizeMap = downloadSizeMap,
                            activeDownloadStateMap = activeDownloadStateMap,
                            activeDownloadSongInfo = activeDownloadSongInfo,
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
                            onDeleteDownload = { viewModel.deleteDownload(it) },
                            onCancelDownload = { songActionsViewModel.cancelDownload(it) }
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
                            }
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

                        4 -> ArtistsTab(
                            artists = artistsArchive,
                            onArtistClick = { artist ->
                                artist.id?.let {
                                    navController.navigate(Screen.Artist.createRoute(it))
                                }
                            }
                        )

                        else -> AlbumsTab(
                            albums = albumsArchive,
                            onAlbumClick = { album ->
                                navController.navigate(Screen.Album.createRoute(album.id))
                            }
                        )
                    }
                }
            }
        }
    }

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
private fun LibraryQuickActionCard(
    title: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    PremiumGlassSurface(
        modifier = Modifier
            .width(132.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlaylistsTab(
    playlists: List<LocalPlaylist>,
    onPlaylistClick: (LocalPlaylist) -> Unit,
    onCreatePlaylist: () -> Unit,
    onDeletePlaylist: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            PremiumSectionHeader(
                title = "Playlists",
                subtitle = "Editable local collections"
            )
        }

        item {
            PremiumGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable { onCreatePlaylist() }
            ) {
                ListItem(
                    headlineContent = { Text("Create new playlist") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null
                        )
                    }
                )
            }
        }

        items(playlists, key = { it.id }) { playlist ->
            var showMenu by remember { mutableStateOf(false) }

            PremiumGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable { onPlaylistClick(playlist) }
            ) {
                ListItem(
                    headlineContent = { Text(playlist.name) },
                    supportingContent = { Text("${playlist.songCount} songs") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null
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
                                        Icon(Icons.Default.DeleteOutline, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

@Composable
private fun DownloadsTab(
    downloads: List<SongItem>,
    downloadSizeMap: Map<String, Long>,
    activeDownloadStateMap: Map<String, com.beatloop.music.ui.viewmodel.SongActionsViewModel.DownloadUiState>,
    activeDownloadSongInfo: Map<String, com.beatloop.music.ui.viewmodel.SongActionsViewModel.DownloadSongInfo>,
    onSongClick: (SongItem) -> Unit,
    onDeleteDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit
) {
    val activeDownloads = activeDownloadStateMap
        .filterValues { it.state == com.beatloop.music.data.model.DownloadState.DOWNLOADING }
        .mapNotNull { (songId, ui) ->
            val info = activeDownloadSongInfo[songId] ?: return@mapNotNull null
            Triple(songId, info, ui)
        }

    if (downloads.isEmpty() && activeDownloads.isEmpty()) {
        PremiumEmptyState(
            icon = Icons.Default.Download,
            title = "No downloads yet",
            message = "Offline-ready songs will show up here."
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                PremiumSectionHeader(
                    title = "Downloads",
                    subtitle = "Offline and active transfers"
                )
            }

            if (activeDownloads.isNotEmpty()) {
                items(activeDownloads, key = { it.first }) { (songId, info, ui) ->
                    PremiumGlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    info.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text("${(ui.progress ?: 0).coerceIn(0, 100)}% • ${info.artist}")
                            },
                            leadingContent = {
                                androidx.compose.material3.CircularProgressIndicator(
                                    progress = { (ui.progress ?: 0).coerceIn(0, 100) / 100f },
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { onCancelDownload(songId) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                                }
                            }
                        )
                    }
                }
            }

            items(downloads, key = { it.id }) { song ->
                var showMenu by remember { mutableStateOf(false) }
                var showDetails by remember { mutableStateOf(false) }

                SongListItem(
                    song = song,
                    onClick = { onSongClick(song) },
                    onMoreClick = { showMenu = true },
                    trailing = {
                        Box {
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("View Details") },
                                    onClick = {
                                        showDetails = true
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Info, contentDescription = null)
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Delete Download") },
                                    onClick = {
                                        onDeleteDownload(song.id)
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                )

                if (showDetails) {
                    val songSize = downloadSizeMap[song.id]
                    AlertDialog(
                        onDismissRequest = { showDetails = false },
                        title = { Text("Download Details") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Title: ${song.title}")
                                Text("Artist: ${song.artistsText}")
                                Text("Size: ${songSize?.let(::formatBytes) ?: "--"}")
                                Text("Path: ${song.localPath ?: "Unavailable"}")
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showDetails = false }) {
                                Text("Close")
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(120.dp))
            }
        }
    }
}

@Composable
private fun HistoryTab(
    history: List<SongItem>,
    onSongClick: (SongItem) -> Unit
) {
    if (history.isEmpty()) {
        PremiumEmptyState(
            icon = Icons.Default.History,
            title = "No history yet",
            message = "Songs you play will appear in your listening timeline."
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                PremiumSectionHeader(
                    title = "History",
                    subtitle = "Recently played tracks"
                )
            }

            items(history, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    onClick = { onSongClick(song) },
                    onMoreClick = { }
                )
            }

            item {
                Spacer(modifier = Modifier.height(120.dp))
            }
        }
    }
}

@Composable
private fun LikedSongsTab(
    likedSongs: List<SongItem>,
    onSongClick: (SongItem) -> Unit,
    onUnlike: (String) -> Unit
) {
    if (likedSongs.isEmpty()) {
        PremiumEmptyState(
            icon = Icons.Default.Favorite,
            title = "No liked songs",
            message = "Heart tracks to build your personal favorites."
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                PremiumSectionHeader(
                    title = "Liked Songs",
                    subtitle = "Your saved favorites"
                )
            }

            items(likedSongs, key = { it.id }) { song ->
                var showMenu by remember { mutableStateOf(false) }
                SongListItem(
                    song = song,
                    onClick = { onSongClick(song) },
                    onMoreClick = { showMenu = true },
                    trailing = {
                        Box {
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Unlike") },
                                    onClick = {
                                        onUnlike(song.id)
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(120.dp))
            }
        }
    }
}

@Composable
private fun ArtistsTab(
    artists: List<ArtistArchive>,
    onArtistClick: (ArtistArchive) -> Unit
) {
    if (artists.isEmpty()) {
        PremiumEmptyState(
            icon = Icons.Default.Person,
            title = "No artists indexed",
            message = "Listen to more tracks to build artist insights."
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                PremiumSectionHeader(
                    title = "Artists",
                    subtitle = "Your most played creators"
                )
            }

            items(artists, key = { it.name }) { artist ->
                PremiumGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { onArtistClick(artist) }
                ) {
                    ListItem(
                        headlineContent = { Text(artist.name) },
                        supportingContent = { Text("${artist.count} tracks") },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null
                            )
                        },
                        trailingContent = {
                            if (artist.id == null) {
                                Text(
                                    text = "Local",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(120.dp))
            }
        }
    }
}

@Composable
private fun AlbumsTab(
    albums: List<AlbumArchive>,
    onAlbumClick: (AlbumArchive) -> Unit
) {
    if (albums.isEmpty()) {
        PremiumEmptyState(
            icon = Icons.Default.Album,
            title = "No albums indexed",
            message = "Album collections will appear when metadata is available."
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                PremiumSectionHeader(
                    title = "Albums",
                    subtitle = "Grouped from your listening archive"
                )
            }

            items(albums, key = { it.id }) { album ->
                PremiumGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { onAlbumClick(album) }
                ) {
                    ListItem(
                        headlineContent = { Text(album.title) },
                        supportingContent = { Text("${album.count} tracks") },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.LibraryMusic,
                                contentDescription = null
                            )
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(120.dp))
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format("%.2f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}
