package com.beatloop.music.domain.recommendation

import javax.inject.Inject
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

class RecommendationFeatureEngineer @Inject constructor() {

    fun buildProfileVector(
        languages: Set<String>,
        singers: Set<String>,
        lyricists: Set<String>,
        musicDirectors: Set<String>,
        searchQueries: List<String>
    ): ProfileVector? {
        val weightedTokens = linkedMapOf<String, Double>()

        addWeightedTokens(weightedTokens, languages, 1.25)
        addWeightedTokens(weightedTokens, singers, 1.7)
        addWeightedTokens(weightedTokens, lyricists, 1.4)
        addWeightedTokens(weightedTokens, musicDirectors, 1.4)
        addWeightedTokens(weightedTokens, searchQueries, 1.1)

        if (weightedTokens.isEmpty()) return null
        return ProfileVector(weightedTokens)
    }

    fun splitArtists(artistsText: String): List<String> {
        return artistsText
            .split(",", "&", "feat.", "ft.", " and ", "/")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    fun tokenizeSong(songText: String): Set<String> {
        return songText
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length > 1 }
            .toSet()
    }

    fun cosineSimilarity(profileVector: ProfileVector?, candidateTokens: Set<String>): Double {
        if (profileVector == null || profileVector.tokenWeights.isEmpty() || candidateTokens.isEmpty()) {
            return 0.0
        }

        val profile = profileVector.tokenWeights
        val dot = candidateTokens.sumOf { token -> profile[token] ?: 0.0 }
        val profileNorm = sqrt(profile.values.sumOf { it * it })
        val candidateNorm = sqrt(candidateTokens.size.toDouble())

        if (profileNorm == 0.0 || candidateNorm == 0.0) return 0.0
        return (dot / (profileNorm * candidateNorm)).coerceIn(0.0, 1.0)
    }

    fun recencyScore(lastPlayedAt: Long?, nowMs: Long): Double {
        if (lastPlayedAt == null) return 0.0
        val ageHours = ((nowMs - lastPlayedAt).coerceAtLeast(0L)).toDouble() / (1000.0 * 60.0 * 60.0)
        return (exp(-ageHours / 36.0) * 100.0).coerceIn(0.0, 100.0)
    }

    fun normalizedPlayScore(playCount: Int): Double {
        if (playCount <= 0) return 0.0
        return (ln(playCount.toDouble() + 1.0) / ln(2.0)).coerceAtMost(8.0)
    }

    private fun addWeightedTokens(
        target: MutableMap<String, Double>,
        values: Collection<String>,
        weight: Double
    ) {
        values
            .asSequence()
            .flatMap { value ->
                value
                    .lowercase()
                    .replace(Regex("[^a-z0-9\\s]"), " ")
                    .split(Regex("\\s+"))
                    .asSequence()
            }
            .map { it.trim() }
            .filter { it.length > 1 }
            .forEach { token ->
                target[token] = (target[token] ?: 0.0) + weight
            }
    }
}
