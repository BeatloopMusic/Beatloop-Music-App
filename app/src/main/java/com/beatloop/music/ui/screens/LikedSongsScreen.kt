package com.beatloop.music.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.beatloop.music.playback.createMediaItem
import com.beatloop.music.ui.LocalPlayerConnection
import com.beatloop.music.ui.components.PremiumEmptyState
import com.beatloop.music.ui.components.SongListItem
import com.beatloop.music.ui.viewmodel.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikedSongsScreen(
    onNavigateBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val likedSongs by viewModel.likedSongs.collectAsState()
    val playerConnection = LocalPlayerConnection.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Liked Songs") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (likedSongs.isEmpty()) {
            PremiumEmptyState(
                title = "No liked songs yet",
                message = "Tap the heart on any track and it will appear in this collection.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                icon = Icons.Default.Favorite
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(likedSongs, key = { it.id }) { song ->
                    SongListItem(
                        song = song,
                        onClick = {
                            val mediaItems = likedSongs.map { item ->
                                createMediaItem(
                                    id = item.id,
                                    title = item.title,
                                    artist = item.artistsText,
                                    thumbnailUrl = item.thumbnailUrl,
                                    localPath = item.localPath
                                )
                            }
                            val startIndex = likedSongs.indexOf(song).coerceAtLeast(0)
                            playerConnection?.setMediaItems(mediaItems, startIndex)
                        },
                        onMoreClick = { }
                    )
                }
            }
        }
    }
}
