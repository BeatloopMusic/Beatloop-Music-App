package com.beatloop.music.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "albums")
data class Album(
    @PrimaryKey
    val id: String,
    val title: String,
    val artistName: String,
    val artistId: String? = null,
    val year: Int? = null,
    val thumbnailUrl: String? = null,
    val songCount: Int = 0,
    val duration: Long = 0,
    val liked: Boolean = false,
    val likedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class AlbumPage(
    val album: AlbumItem,
    val songs: List<SongItem> = emptyList(),
    val description: String? = null,
    val otherVersions: List<AlbumItem> = emptyList()
)
