package com.beatloop.music.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_preference_sync")
data class UserPreferenceSyncEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

@Entity(tableName = "deleted_playlist_sync")
data class DeletedPlaylistSyncEntity(
    @PrimaryKey
    val syncId: String,
    val deletedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

data class PlaylistSyncSnapshot(
    val id: Long,
    val syncId: String,
    val name: String,
    val songIdsCsv: String,
    val lastUpdatedTimestamp: Long,
    val isSynced: Boolean
) {
    fun songIds(): List<String> = songIdsCsv
        .split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() }
}
