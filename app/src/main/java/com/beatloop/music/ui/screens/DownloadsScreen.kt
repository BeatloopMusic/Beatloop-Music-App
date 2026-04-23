package com.beatloop.music.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.beatloop.music.playback.createMediaItem
import com.beatloop.music.ui.LocalPlayerConnection
import com.beatloop.music.ui.components.PremiumEmptyState
import com.beatloop.music.ui.components.SongListItem
import com.beatloop.music.ui.viewmodel.LibraryViewModel
import com.beatloop.music.ui.viewmodel.SongActionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onNavigateBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
    songActionsViewModel: SongActionsViewModel = hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsState()
    val downloadSizeMap by viewModel.downloadSizeMap.collectAsState()
    val activeDownloadStateMap by songActionsViewModel.downloadUiStateMap.collectAsState()
    val activeDownloadSongInfo by songActionsViewModel.activeDownloadSongInfo.collectAsState()
    val playerConnection = LocalPlayerConnection.current

    val activeDownloads = remember(activeDownloadStateMap, activeDownloadSongInfo) {
        activeDownloadStateMap
            .filterValues { it.state == com.beatloop.music.data.model.DownloadState.DOWNLOADING }
            .mapNotNull { (songId, ui) ->
                val info = activeDownloadSongInfo[songId] ?: return@mapNotNull null
                Triple(songId, info, ui)
            }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (downloads.isEmpty() && activeDownloads.isEmpty()) {
            PremiumEmptyState(
                title = "No downloads yet",
                message = "Downloaded tracks and progress will show up here for quick offline access.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                icon = Icons.Default.Download
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (activeDownloads.isNotEmpty()) {
                    item {
                        Text(
                            text = "Active Downloads",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    items(activeDownloads, key = { it.first }) { (songId, info, ui) ->
                        Card {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        progress = { (ui.progress ?: 0).coerceIn(0, 100) / 100f },
                                        modifier = Modifier.size(38.dp),
                                        strokeWidth = 3.dp,
                                        strokeCap = StrokeCap.Round
                                    )
                                    Text(
                                        text = "${(ui.progress ?: 0).coerceIn(0, 100)}%",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = info.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = info.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(onClick = { songActionsViewModel.cancelDownload(songId) }) {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = "Cancel download",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                if (downloads.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    item {
                        Text(
                            text = "Downloaded",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                items(downloads, key = { it.id }) { song ->
                    var showMenu by remember { mutableStateOf(false) }
                    var showDetails by remember { mutableStateOf(false) }
                    Column {
                        SongListItem(
                            song = song,
                            onClick = {
                                playerConnection?.setMediaItem(
                                    createMediaItem(
                                        id = song.id,
                                        title = song.title,
                                        artist = song.artistsText,
                                        thumbnailUrl = song.thumbnailUrl,
                                        localPath = song.localPath
                                    )
                                )
                            },
                            onMoreClick = { showMenu = true },
                            trailing = {
                                Box {
                                    IconButton(onClick = { showMenu = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("View Details") },
                                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                            onClick = {
                                                showDetails = true
                                                showMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete Download") },
                                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                            onClick = {
                                                viewModel.deleteDownload(song.id)
                                                showMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        )

                        val songSize = downloadSizeMap[song.id]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Downloaded",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = songSize?.let(::formatBytes) ?: "--",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (showDetails) {
                                AlertDialog(
                                    onDismissRequest = { showDetails = false },
                                    title = { Text("Download Details") },
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    }
                }
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
