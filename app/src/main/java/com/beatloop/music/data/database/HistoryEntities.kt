package com.beatloop.music.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_history")
data class SearchHistory(
    @PrimaryKey
    val query: String,
    val timestamp: Long = System.currentTimeMillis(),
    val searchCount: Int = 1,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

@Entity(tableName = "play_history")
data class PlayHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val songId: String,
    val playedAt: Long = System.currentTimeMillis()
)
