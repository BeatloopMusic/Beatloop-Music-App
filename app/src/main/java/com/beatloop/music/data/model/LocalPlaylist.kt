package com.beatloop.music.data.model

import androidx.room.Embedded
import androidx.room.Ignore
import androidx.room.Junction
import androidx.room.Relation
import com.beatloop.music.data.database.PlaylistEntity
import com.beatloop.music.data.database.PlaylistSongCrossRef

data class LocalPlaylist(
    val id: Long,
    val name: String,
    val songCount: Int = 0
)

data class LocalPlaylistWithSongs(
    @Embedded
    val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = PlaylistSongCrossRef::class,
            parentColumn = "playlistId",
            entityColumn = "songId"
        )
    )
    val songs: List<Song>
)

data class PlaylistContinuation(
    val songs: List<SongItem>,
    val continuation: String? = null
)
