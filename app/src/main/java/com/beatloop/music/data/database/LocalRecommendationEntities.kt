package com.beatloop.music.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_history",
    indices = [
        Index(value = ["songId"]),
        Index(value = ["timestamp"])
    ]
)
data class UserHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val songId: String,
    val timestamp: Long,
    val skipped: Boolean,
    val listenDuration: Long
)

@Entity(
    tableName = "recommendation_songs",
    indices = [
        Index(value = ["artist"]),
        Index(value = ["genre"]),
        Index(value = ["popularity"])
    ]
)
data class SongEntity(
    @PrimaryKey
    val songId: String,
    val title: String,
    val artist: String,
    val genre: String,
    val tempo: Float,
    val mood: String,
    val popularity: Float
)

@Entity(tableName = "song_embeddings")
data class SongEmbeddingEntity(
    @PrimaryKey
    val songId: String,
    val embedding: FloatArray
)
