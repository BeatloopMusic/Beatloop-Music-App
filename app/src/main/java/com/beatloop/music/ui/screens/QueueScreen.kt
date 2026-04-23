package com.beatloop.music.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.beatloop.music.ui.components.PremiumEmptyState
import com.beatloop.music.ui.components.SongListItem
import com.beatloop.music.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    onNavigateBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val queue by viewModel.queue.collectAsState()
    val isLocked by viewModel.isQueueLocked.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Queue") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleQueueLock() }) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.Lock,
                            contentDescription = "Lock Queue",
                            tint = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (queue.isEmpty()) {
            PremiumEmptyState(
                title = "Queue is empty",
                message = "Start playback from Home or Search and upcoming tracks will appear here.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                icon = Icons.AutoMirrored.Filled.QueueMusic
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(queue, key = { _, item -> item.id }) { index, song ->
                    SongListItem(
                        song = song,
                        onClick = { viewModel.playFromQueue(index) },
                        onMoreClick = { }
                    )
                }
            }
        }
    }
}
