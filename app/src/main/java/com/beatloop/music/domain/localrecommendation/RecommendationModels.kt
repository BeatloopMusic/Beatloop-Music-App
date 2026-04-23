package com.beatloop.music.domain.localrecommendation

data class Song(
    val songId: String,
    val title: String,
    val artist: String,
    val genre: String,
    val tempo: Float,
    val mood: String,
    val popularity: Float
)

data class UserHistory(
    val songId: String,
    val timestamp: Long,
    val skipped: Boolean,
    val listenDuration: Long
)

data class SongFeatures(
    val genreSignal: Float,
    val moodEnergy: Float,
    val tempoNormalized: Float,
    val popularityNormalized: Float,
    val recencySignal: Float,
    val skipSignal: Float
) {
    fun toVector(): FloatArray {
        return floatArrayOf(
            genreSignal,
            moodEnergy,
            tempoNormalized,
            popularityNormalized,
            recencySignal,
            skipSignal
        )
    }
}

data class RecommendationResult(
    val song: Song,
    val score: Float
)

data class RecommendationContext(
    val hourOfDay: Int,
    val isWorkout: Boolean,
    val isFocusMode: Boolean,
    val topN: Int = 20
)
