package com.beatloop.music.domain.recommendation

import com.beatloop.music.data.model.SongItem

enum class RecommendationSource {
    QUICK_PICK,
    TRENDING,
    RECENT,
    PERSONALIZATION_SEED,
    ONBOARDING,
    RELATED,
    HISTORY,
    UNKNOWN
}

data class RecommendationCandidate(
    val song: SongItem,
    val source: RecommendationSource = RecommendationSource.UNKNOWN,
    val sourceBoost: Double = 0.0
)

data class SongBehaviorSignal(
    val playCount: Int = 0,
    val totalSessions: Int = 0,
    val skipCount: Int = 0,
    val completedCount: Int = 0,
    val averageCompletionRate: Double = 0.0,
    val totalListenedMs: Long = 0,
    val lastPlayedAt: Long? = null
)

data class SongInteractionSignal(
    val likeCount: Double = 0.0,
    val unlikeCount: Double = 0.0,
    val replayCount: Double = 0.0,
    val playlistAddCount: Double = 0.0,
    val lastInteractionAt: Long? = null
)

data class ProfileVector(
    val tokenWeights: Map<String, Double>
)

data class RecommendationContext(
    val nowMs: Long = System.currentTimeMillis(),
    val recentSongIds: Set<String> = emptySet(),
    val recentRecommendationIds: Set<String> = emptySet(),
    val queueSongIds: Set<String> = emptySet(),
    val topArtistWeights: Map<String, Double> = emptyMap(),
    val preferredLanguages: Set<String> = emptySet(),
    val preferredSingers: Set<String> = emptySet(),
    val preferredLyricists: Set<String> = emptySet(),
    val preferredMusicDirectors: Set<String> = emptySet(),
    val transitionWeights: Map<String, Double> = emptyMap(),
    val profileVector: ProfileVector? = null
)

data class RecommendationFeatures(
    val playCount: Int,
    val skipRate: Double,
    val completionRate: Double,
    val recencyScore: Double,
    val profileSimilarity: Double,
    val artistAffinityScore: Double,
    val interactionScore: Double,
    val transitionScore: Double,
    val sourceScore: Double,
    val explorationScore: Double,
    val antiRepetitionPenalty: Double
)

data class ScoredRecommendation(
    val candidate: RecommendationCandidate,
    val features: RecommendationFeatures,
    val finalScore: Double
)
