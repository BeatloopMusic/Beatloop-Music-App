package com.beatloop.music.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "listening_session_events",
    indices = [
        Index(value = ["songId"]),
        Index(value = ["playedAt"]),
        Index(value = ["previousSongId"]),
        Index(value = ["nextSongId"])
    ]
)
data class ListeningSessionEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val songId: String,
    val previousSongId: String? = null,
    val nextSongId: String? = null,
    val playedAt: Long = System.currentTimeMillis(),
    val listenedMs: Long = 0,
    val trackDurationMs: Long? = null,
    val completionRate: Float = 0f,
    val wasSkipped: Boolean = false,
    val wasCompleted: Boolean = false,
    val source: String = "player"
)

@Entity(
    tableName = "interaction_signals",
    indices = [
        Index(value = ["songId"]),
        Index(value = ["createdAt"]),
        Index(value = ["signalType"]),
        Index(value = ["query"])
    ]
)
data class InteractionSignal(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val songId: String? = null,
    val query: String? = null,
    val signalType: String,
    val value: Float = 1f,
    val metadata: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "recommendation_impressions",
    indices = [
        Index(value = ["songId"]),
        Index(value = ["shownAt"]),
        Index(value = ["surface"])
    ]
)
data class RecommendationImpression(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val songId: String,
    val surface: String,
    val shownAt: Long = System.currentTimeMillis()
)

object InteractionSignalTypes {
    const val LIKE = "LIKE"
    const val UNLIKE = "UNLIKE"
    const val PLAYLIST_ADD = "PLAYLIST_ADD"
    const val SEARCH = "SEARCH"
    const val REPLAY = "REPLAY"
    const val SKIP = "SKIP"
    const val COMPLETE = "COMPLETE"
}
