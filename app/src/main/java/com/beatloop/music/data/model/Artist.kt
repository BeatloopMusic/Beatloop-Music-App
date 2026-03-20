package com.beatloop.music.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "artists")
data class Artist(
    @PrimaryKey
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val subscriberCount: String? = null,
    val followed: Boolean = false,
    val followedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class ArtistPage(
    val artist: ArtistItem,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val songs: List<SongItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val singles: List<AlbumItem> = emptyList(),
    val similarArtists: List<ArtistItem> = emptyList()
)
