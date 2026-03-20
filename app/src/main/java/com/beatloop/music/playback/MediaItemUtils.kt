package com.beatloop.music.playback

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/**
 * Creates a MediaItem for playback
 */
fun createMediaItem(
    id: String,
    title: String,
    artist: String,
    thumbnailUrl: String?,
    albumTitle: String? = null,
    duration: Long? = null,
    localPath: String? = null
): MediaItem {
    val artworkUri = thumbnailUrl?.toUri()
    
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(albumTitle)
        .setArtworkUri(artworkUri)
        .build()
    
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(localPath?.toUri() ?: "beatloop://song/$id".toUri())
        .setMediaMetadata(metadata)
        .build()
}

/**
 * Extension to get song ID from MediaItem
 */
val MediaItem.songId: String
    get() = mediaId

/**
 * Format duration in milliseconds to MM:SS or HH:MM:SS
 */
fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * Parse duration string like "3:45" to milliseconds
 */
fun parseDuration(duration: String): Long {
    val parts = duration.split(":").map { it.toIntOrNull() ?: 0 }
    return when (parts.size) {
        2 -> (parts[0] * 60 + parts[1]) * 1000L
        3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
        else -> 0L
    }
}
