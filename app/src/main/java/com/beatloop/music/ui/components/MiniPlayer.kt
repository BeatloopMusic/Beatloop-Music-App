package com.beatloop.music.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.beatloop.music.ui.PlayerConnection

@Composable
fun MiniPlayer(
    playerConnection: PlayerConnection?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connection = playerConnection ?: return
    val isPlaying by connection.isPlaying.collectAsState()
    val currentMediaItem = connection.currentMediaItem ?: return
    val currentPosition by connection.currentPosition.collectAsState()
    val duration by connection.duration.collectAsState()
    
    val progress = if (duration > 0) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(220),
        label = "mini_player_progress"
    )
    
    PremiumGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp
    ) {
        Column {
            // Progress indicator
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Album art
                    AsyncImage(
                        model = currentMediaItem.mediaMetadata.artworkUri
                            ?.toString()
                            ?.replace(Regex("=w\\d+-h\\d+"), "=w600-h600")
                            ?.replace(Regex("=s\\d+"), "=s600"),
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Song info
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = currentMediaItem.mediaMetadata.title?.toString() ?: "Unknown",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentMediaItem.mediaMetadata.artist?.toString() ?: "Unknown Artist",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(onClick = { connection.playPause() }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(onClick = { connection.seekToNext() }) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
