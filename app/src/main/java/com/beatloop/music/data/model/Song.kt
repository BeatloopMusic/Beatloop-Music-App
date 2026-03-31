package com.beatloop.music.data.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@Entity(tableName = "songs")
data class Song(
    @PrimaryKey
    val id: String,
    val title: String,
    val artistsText: String,
    val artistId: String? = null,
    val albumName: String? = null,
    val albumId: String? = null,
    val duration: Long = 0,
    val thumbnailUrl: String? = null,
    val liked: Boolean = false,
    val likedAt: Long? = null,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val downloadState: DownloadState = DownloadState.NOT_DOWNLOADED,
    val localPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdatedTimestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

@Serializable
enum class DownloadState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}

// Simple data class for Room database queries - no complex types
@Serializable
data class SongItem(
    val id: String,
    val title: String,
    val artistsText: String = "",
    val artistId: String? = null,
    val thumbnailUrl: String? = null,
    val albumId: String? = null,
    val duration: Long? = null,
    val localPath: String? = null
) {
    companion object {
        fun fromApiResponse(
            id: String,
            title: String,
            artists: List<ArtistItem>,
            thumbnailUrl: String? = null,
            albumId: String? = null,
            duration: Long? = null
        ) = SongItem(
            id = id,
            title = title,
            artistsText = artists.joinToString(", ") { it.name },
            artistId = artists.firstOrNull()?.id,
            thumbnailUrl = thumbnailUrl,
            albumId = albumId,
            duration = duration
        )
    }
}

@Serializable
data class ArtistItem(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val subscriberCount: String? = null
) {
    // Alias for subscriberCount
    val subscribersText: String?
        get() = subscriberCount
}

@Serializable
data class AlbumItem(
    val id: String,
    val title: String,
    val artists: List<ArtistItem> = emptyList(),
    val year: Int? = null,
    val thumbnailUrl: String? = null,
    val songCount: Int? = null,
    val duration: Long? = null,
    val explicit: Boolean = false
) {
    val artistsText: String
        get() = artists.joinToString(", ") { it.name }
}

@Serializable
data class PlaylistItem(
    val id: String,
    val title: String,
    val author: String? = null,
    val thumbnailUrl: String? = null,
    val songCount: Int? = null,
    val isEditable: Boolean = false,
    val year: Int? = null
)

@Serializable
data class VideoItem(
    val id: String,
    val title: String,
    val artists: List<ArtistItem>,
    val thumbnailUrl: String? = null,
    val duration: Long = 0,
    val views: String? = null
) {
    val artistsText: String
        get() = artists.joinToString(", ") { it.name }
}
