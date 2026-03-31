package com.beatloop.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.beatloop.music.data.model.SongItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsBottomSheet(
    song: SongItem,
    isLiked: Boolean = false,
    isDownloaded: Boolean = false,
    downloadProgress: Int? = null,
    downloadSizeBytes: Long? = null,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onDownload: () -> Unit,
    onGoToArtist: () -> Unit,
    onGoToAlbum: () -> Unit,
    onShare: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Song Info Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artistsText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val downloadStatusText = when {
                        isDownloaded && downloadSizeBytes != null -> "Downloaded • ${formatBytes(downloadSizeBytes)}"
                        isDownloaded -> "Downloaded"
                        downloadProgress != null -> "Downloading • ${downloadProgress.coerceIn(0, 100)}%"
                        else -> null
                    }

                    if (downloadStatusText != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = downloadStatusText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (downloadProgress != null && !isDownloaded) {
                LinearProgressIndicator(
                    progress = { downloadProgress.coerceIn(0, 100) / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Options
            BottomSheetOption(
                icon = Icons.Default.PlaylistPlay,
                text = "Play next",
                onClick = {
                    onPlayNext()
                    onDismiss()
                }
            )
            
            BottomSheetOption(
                icon = Icons.Default.AddToQueue,
                text = "Add to queue",
                onClick = {
                    onAddToQueue()
                    onDismiss()
                }
            )
            
            BottomSheetOption(
                icon = if (isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                text = if (isLiked) "Remove from Liked" else "Add to Liked",
                onClick = {
                    onLike()
                    onDismiss()
                }
            )
            
            if (onRemoveFromPlaylist != null) {
                BottomSheetOption(
                    icon = Icons.Default.PlaylistRemove,
                    text = "Remove from playlist",
                    onClick = {
                        onRemoveFromPlaylist()
                        onDismiss()
                    }
                )
            }

            BottomSheetOption(
                icon = if (isDownloaded) Icons.Default.DownloadDone else Icons.Outlined.Download,
                text = when {
                    isDownloaded && downloadSizeBytes != null -> "Downloaded (${formatBytes(downloadSizeBytes)})"
                    isDownloaded -> "Downloaded"
                    downloadProgress != null -> "Downloading ${downloadProgress.coerceIn(0, 100)}%"
                    else -> "Download"
                },
                enabled = !isDownloaded && downloadProgress == null,
                onClick = {
                    if (!isDownloaded) {
                        onDownload()
                    }
                    onDismiss()
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            BottomSheetOption(
                icon = Icons.Default.Person,
                text = "Go to artist",
                enabled = song.artistId != null,
                onClick = {
                    if (song.artistId != null) {
                        onGoToArtist()
                    }
                    onDismiss()
                }
            )
            
            song.albumId?.let {
                BottomSheetOption(
                    icon = Icons.Default.Album,
                    text = "Go to album",
                    onClick = {
                        onGoToAlbum()
                        onDismiss()
                    }
                )
            }
            
            BottomSheetOption(
                icon = Icons.Default.Share,
                text = "Share",
                onClick = {
                    onShare()
                    onDismiss()
                }
            )
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

@Composable
private fun BottomSheetOption(
    icon: ImageVector,
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = text,
                color = if (enabled) 
                    MaterialTheme.colorScheme.onSurface 
                else 
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) 
                    MaterialTheme.colorScheme.onSurfaceVariant 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        },
        modifier = Modifier.clickable(enabled = enabled) { onClick() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistBottomSheet(
    playlists: List<com.beatloop.music.data.model.LocalPlaylist>,
    onDismiss: () -> Unit,
    onCreateNew: () -> Unit,
    onSelectPlaylist: (Long) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Add to playlist",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            
            // Create new playlist option
            ListItem(
                headlineContent = { Text("Create new playlist") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { 
                    onCreateNew()
                    onDismiss()
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Existing playlists
            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No playlists yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                playlists.forEach { playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.name) },
                        supportingContent = { Text("${playlist.songCount} songs") },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.PlaylistPlay,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.clickable { 
                            onSelectPlaylist(playlist.id)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}
