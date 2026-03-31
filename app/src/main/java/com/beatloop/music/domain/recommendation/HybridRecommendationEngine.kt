package com.beatloop.music.domain.recommendation

import javax.inject.Inject

class HybridRecommendationEngine @Inject constructor(
    private val featureEngineer: RecommendationFeatureEngineer
) {

    fun rankCandidates(
        candidates: List<RecommendationCandidate>,
        behaviorBySong: Map<String, SongBehaviorSignal>,
        interactionBySong: Map<String, SongInteractionSignal>,
        context: RecommendationContext,
        limit: Int
    ): List<ScoredRecommendation> {
        if (candidates.isEmpty() || limit <= 0) return emptyList()

        val scored = candidates
            .asSequence()
            .filter { candidate ->
                candidate.song.id.isNotBlank() &&
                    !context.queueSongIds.contains(candidate.song.id) &&
                    RecommendationContentRules.isTrackAllowed(candidate.song) &&
                    matchesLanguagePolicy(candidate, context)
            }
            .map { candidate ->
                val songId = candidate.song.id
                val behavior = behaviorBySong[songId] ?: SongBehaviorSignal()
                val interaction = interactionBySong[songId] ?: SongInteractionSignal()
                val features = buildFeatures(candidate, behavior, interaction, context)
                ScoredRecommendation(
                    candidate = candidate,
                    features = features,
                    finalScore = aggregateScore(features)
                )
            }
            .sortedByDescending { it.finalScore }
            .toList()

        return enforceArtistDiversity(scored).take(limit)
    }

    fun rankNextSongPredictions(
        seedSongId: String,
        candidates: List<RecommendationCandidate>,
        behaviorBySong: Map<String, SongBehaviorSignal>,
        interactionBySong: Map<String, SongInteractionSignal>,
        context: RecommendationContext,
        limit: Int
    ): List<ScoredRecommendation> {
        if (seedSongId.isBlank()) {
            return rankCandidates(candidates, behaviorBySong, interactionBySong, context, limit)
        }

        val boostedContext = context.copy(
            transitionWeights = context.transitionWeights.mapValues { (_, value) -> value * 1.35 }
        )

        return rankCandidates(
            candidates = candidates,
            behaviorBySong = behaviorBySong,
            interactionBySong = interactionBySong,
            context = boostedContext,
            limit = limit
        )
    }

    private fun buildFeatures(
        candidate: RecommendationCandidate,
        behavior: SongBehaviorSignal,
        interaction: SongInteractionSignal,
        context: RecommendationContext
    ): RecommendationFeatures {
        val song = candidate.song
        val songId = song.id
        val totalSessions = behavior.totalSessions.coerceAtLeast(0)
        val skipRate = if (totalSessions > 0) {
            behavior.skipCount.toDouble() / totalSessions.toDouble()
        } else {
            0.0
        }.coerceIn(0.0, 1.0)

        val completionRate = when {
            behavior.averageCompletionRate > 0.0 -> behavior.averageCompletionRate
            totalSessions > 0 -> behavior.completedCount.toDouble() / totalSessions.toDouble()
            else -> 0.0
        }.coerceIn(0.0, 1.0)

        val recency = featureEngineer.recencyScore(behavior.lastPlayedAt, context.nowMs)

        val songText = "${song.title} ${song.artistsText}".trim()
        val tokens = featureEngineer.tokenizeSong(songText)
        val profileSimilarity = featureEngineer.cosineSimilarity(context.profileVector, tokens)

        val artistAffinity = featureEngineer
            .splitArtists(song.artistsText)
            .maxOfOrNull { artist -> context.topArtistWeights[artist.lowercase()] ?: 0.0 }
            ?: 0.0

        val candidateText = "${song.title} ${song.artistsText}".lowercase()
        val singerPreferenceScore = context.preferredSingers.sumOf { singer ->
            if (song.artistsText.contains(singer, ignoreCase = true)) 5.0 else 0.0
        }
        val lyricistPreferenceScore = context.preferredLyricists.sumOf { lyricist ->
            if (candidateText.contains(lyricist.lowercase())) 3.5 else 0.0
        }
        val directorPreferenceScore = context.preferredMusicDirectors.sumOf { director ->
            if (candidateText.contains(director.lowercase())) 3.5 else 0.0
        }
        val languagePreferenceScore = context.preferredLanguages.sumOf { language ->
            if (candidateText.contains(language.lowercase())) 2.5 else 0.0
        }
        val preferenceHintScore =
            singerPreferenceScore + lyricistPreferenceScore + directorPreferenceScore + languagePreferenceScore

        val interactionScore =
            (interaction.likeCount * 14.0) +
            (interaction.playlistAddCount * 10.0) +
            (interaction.replayCount * 6.0) -
            (interaction.unlikeCount * 18.0)

        val transitionScore = context.transitionWeights[songId] ?: 0.0
        val sourceScore = candidate.sourceBoost
        val explorationScore = if (behavior.totalSessions == 0 && interaction.likeCount == 0.0) 8.0 else 0.0

        val antiRepetitionPenalty = when {
            context.recentRecommendationIds.contains(songId) -> 16.0
            context.recentSongIds.contains(songId) -> 11.0
            else -> 0.0
        }

        return RecommendationFeatures(
            playCount = behavior.playCount,
            skipRate = skipRate,
            completionRate = completionRate,
            recencyScore = recency,
            profileSimilarity = profileSimilarity,
            artistAffinityScore = artistAffinity + preferenceHintScore,
            interactionScore = interactionScore,
            transitionScore = transitionScore,
            sourceScore = sourceScore,
            explorationScore = explorationScore,
            antiRepetitionPenalty = antiRepetitionPenalty
        )
    }

    private fun aggregateScore(features: RecommendationFeatures): Double {
        val behaviorScore =
            (featureEngineer.normalizedPlayScore(features.playCount) * 4.5) +
            (features.completionRate * 26.0) +
            ((1.0 - features.skipRate) * 18.0) +
            (features.recencyScore * 0.25)

        val profileScore = (features.profileSimilarity * 24.0) + (features.artistAffinityScore * 0.6)

        return behaviorScore +
            profileScore +
            features.interactionScore +
            (features.transitionScore * 1.9) +
            features.sourceScore +
            features.explorationScore -
            features.antiRepetitionPenalty
    }

    private fun matchesLanguagePolicy(
        candidate: RecommendationCandidate,
        context: RecommendationContext
    ): Boolean {
        if (context.enforcedLanguages.isEmpty()) return true

        val allowUnknownLanguage = candidate.source == RecommendationSource.RECENT ||
            candidate.source == RecommendationSource.PERSONALIZATION_SEED ||
            candidate.source == RecommendationSource.ONBOARDING

        return RecommendationContentRules.matchesAllowedLanguages(
            song = candidate.song,
            allowedLanguages = context.enforcedLanguages,
            allowUnknownLanguage = allowUnknownLanguage
        )
    }

    private fun enforceArtistDiversity(scored: List<ScoredRecommendation>): List<ScoredRecommendation> {
        if (scored.size <= 2) return scored

        val output = mutableListOf<ScoredRecommendation>()
        val deferred = mutableListOf<ScoredRecommendation>()
        val recentArtists = ArrayDeque<String>()

        scored.forEach { item ->
            val primaryArtist = featureEngineer
                .splitArtists(item.candidate.song.artistsText)
                .firstOrNull()
                .orEmpty()
                .lowercase()

            if (primaryArtist.isNotBlank() && recentArtists.contains(primaryArtist)) {
                deferred += item
            } else {
                output += item
                if (primaryArtist.isNotBlank()) {
                    recentArtists.addLast(primaryArtist)
                    if (recentArtists.size > 2) {
                        recentArtists.removeFirst()
                    }
                }
            }
        }

        return output + deferred
    }
}
