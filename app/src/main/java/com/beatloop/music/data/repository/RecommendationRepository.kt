package com.beatloop.music.data.repository

import com.beatloop.music.data.database.InteractionSignal
import com.beatloop.music.data.database.InteractionSignalTypes
import com.beatloop.music.data.database.ListeningSessionEvent
import com.beatloop.music.data.database.RecommendationDao
import com.beatloop.music.data.database.RecommendationImpression
import com.beatloop.music.data.database.SongBehaviorMetrics
import com.beatloop.music.data.database.SongDao
import com.beatloop.music.data.database.SongInteractionMetrics
import com.beatloop.music.data.model.SongItem
import com.beatloop.music.data.preferences.PreferencesManager
import com.beatloop.music.domain.recommendation.HybridRecommendationEngine
import com.beatloop.music.domain.recommendation.ProfileVector
import com.beatloop.music.domain.recommendation.RecommendationCandidate
import com.beatloop.music.domain.recommendation.RecommendationContext
import com.beatloop.music.domain.recommendation.RecommendationFeatureEngineer
import com.beatloop.music.domain.recommendation.SongBehaviorSignal
import com.beatloop.music.domain.recommendation.SongInteractionSignal
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

@Singleton
class RecommendationRepository @Inject constructor(
    private val songDao: SongDao,
    private val recommendationDao: RecommendationDao,
    private val preferencesManager: PreferencesManager,
    private val featureEngineer: RecommendationFeatureEngineer,
    private val hybridRecommendationEngine: HybridRecommendationEngine
) {

    private data class RecommendationCacheEntry(
        val key: String,
        val songs: List<SongItem>,
        val timestampMs: Long,
        val version: Long
    )

    private data class RecommendationProfile(
        val languages: Set<String> = emptySet(),
        val singers: Set<String> = emptySet(),
        val lyricists: Set<String> = emptySet(),
        val musicDirectors: Set<String> = emptySet(),
        val vector: ProfileVector? = null
    )

    private val cacheLock = Any()
    private var recommendationVersion: Long = 0
    private var homeRecommendationCache: RecommendationCacheEntry? = null
    private var lastCleanupAtMs: Long = 0

    suspend fun rankHomeCandidates(
        candidates: List<RecommendationCandidate>,
        topArtistWeights: Map<String, Int>,
        refreshNonce: Long,
        limit: Int = 40
    ): List<SongItem> {
        if (candidates.isEmpty() || limit <= 0) return emptyList()

        val now = System.currentTimeMillis()
        val currentVersion = synchronized(cacheLock) { recommendationVersion }
        val cacheKey = buildHomeCacheKey(candidates, topArtistWeights, refreshNonce)

        synchronized(cacheLock) {
            val cached = homeRecommendationCache
            if (
                cached != null &&
                cached.key == cacheKey &&
                cached.version == currentVersion &&
                (now - cached.timestampMs) <= HOME_CACHE_TTL_MS
            ) {
                return rotateForRefresh(cached.songs, refreshNonce).take(limit)
            }
        }

        val profile = loadRecommendationProfile()
        val behaviorSignals = buildBehaviorSignals(now)
        val interactionSignals = buildInteractionSignals(now)
        val recentListened = recommendationDao.getRecentlyListenedSongIds(limit = 60).toSet()
        val recentRecommended = recommendationDao
            .getRecentlyRecommendedSongIds(sinceMs = now - RECENT_RECOMMENDATION_WINDOW_MS, limit = 80)
            .toSet()

        val context = RecommendationContext(
            nowMs = now,
            recentSongIds = recentListened,
            recentRecommendationIds = recentRecommended,
            topArtistWeights = topArtistWeights.mapKeys { (artist, _) -> artist.trim().lowercase() }
                .mapValues { (_, weight) -> weight.toDouble() },
            preferredLanguages = profile.languages,
            preferredSingers = profile.singers,
            preferredLyricists = profile.lyricists,
            preferredMusicDirectors = profile.musicDirectors,
            profileVector = profile.vector
        )

        val ranked = hybridRecommendationEngine
            .rankCandidates(
                candidates = candidates,
                behaviorBySong = behaviorSignals,
                interactionBySong = interactionSignals,
                context = context,
                limit = limit
            )
            .map { it.candidate.song }

        val rotated = rotateForRefresh(ranked, refreshNonce).take(limit)
        synchronized(cacheLock) {
            homeRecommendationCache = RecommendationCacheEntry(
                key = cacheKey,
                songs = rotated,
                timestampMs = now,
                version = currentVersion
            )
        }
        return rotated
    }

    suspend fun rankNextSongCandidates(
        seedSongId: String?,
        candidates: List<RecommendationCandidate>,
        queueSongIds: Set<String>,
        limit: Int = 20
    ): List<SongItem> {
        if (candidates.isEmpty() || limit <= 0) return emptyList()

        val now = System.currentTimeMillis()
        val profile = loadRecommendationProfile()
        val behaviorSignals = buildBehaviorSignals(now)
        val interactionSignals = buildInteractionSignals(now)
        val recentListened = recommendationDao.getRecentlyListenedSongIds(limit = 60).toSet()
        val recentRecommended = recommendationDao
            .getRecentlyRecommendedSongIds(sinceMs = now - RECENT_RECOMMENDATION_WINDOW_MS, limit = 80)
            .toSet()

        val transitionWeights = if (seedSongId.isNullOrBlank()) {
            emptyMap()
        } else {
            recommendationDao
                .getNextSongTransitionMetrics(seedSongId, limit = 40)
                .associate { metric -> metric.songId to metric.transitionCount.toDouble() }
        }

        val context = RecommendationContext(
            nowMs = now,
            recentSongIds = recentListened,
            recentRecommendationIds = recentRecommended,
            queueSongIds = queueSongIds,
            topArtistWeights = emptyMap(),
            preferredLanguages = profile.languages,
            preferredSingers = profile.singers,
            preferredLyricists = profile.lyricists,
            preferredMusicDirectors = profile.musicDirectors,
            transitionWeights = transitionWeights,
            profileVector = profile.vector
        )

        val ranked = if (seedSongId.isNullOrBlank()) {
            hybridRecommendationEngine.rankCandidates(
                candidates = candidates,
                behaviorBySong = behaviorSignals,
                interactionBySong = interactionSignals,
                context = context,
                limit = limit
            )
        } else {
            hybridRecommendationEngine.rankNextSongPredictions(
                seedSongId = seedSongId,
                candidates = candidates,
                behaviorBySong = behaviorSignals,
                interactionBySong = interactionSignals,
                context = context,
                limit = limit
            )
        }

        return ranked.map { it.candidate.song }.take(limit)
    }

    suspend fun recordListeningEvent(event: ListeningSessionEvent) {
        recommendationDao.insertListeningEvent(event)

        if (event.wasSkipped) {
            recommendationDao.insertInteraction(
                InteractionSignal(
                    songId = event.songId,
                    signalType = InteractionSignalTypes.SKIP,
                    value = 1f,
                    createdAt = event.playedAt
                )
            )
        }

        if (event.wasCompleted) {
            recommendationDao.insertInteraction(
                InteractionSignal(
                    songId = event.songId,
                    signalType = InteractionSignalTypes.COMPLETE,
                    value = 1f,
                    createdAt = event.playedAt
                )
            )
        }

        if (!event.nextSongId.isNullOrBlank() && event.nextSongId == event.songId) {
            recommendationDao.insertInteraction(
                InteractionSignal(
                    songId = event.songId,
                    signalType = InteractionSignalTypes.REPLAY,
                    value = 1f,
                    createdAt = event.playedAt
                )
            )
        }

        bumpRecommendationVersion()
        cleanupTrackingDataIfNeeded(event.playedAt)
    }

    suspend fun recordSongInteraction(
        songId: String,
        signalType: String,
        value: Float = 1f,
        metadata: String? = null,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        if (songId.isBlank()) return
        recommendationDao.insertInteraction(
            InteractionSignal(
                songId = songId,
                signalType = signalType,
                value = value,
                metadata = metadata,
                createdAt = timestampMs
            )
        )
        bumpRecommendationVersion()
    }

    suspend fun recordSearchQuery(query: String, timestampMs: Long = System.currentTimeMillis()) {
        if (query.isBlank()) return
        recommendationDao.insertInteraction(
            InteractionSignal(
                query = query.trim(),
                signalType = InteractionSignalTypes.SEARCH,
                value = 1f,
                createdAt = timestampMs
            )
        )
        bumpRecommendationVersion()
    }

    suspend fun recordRecommendationImpressions(
        songs: List<SongItem>,
        surface: String,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        if (songs.isEmpty() || surface.isBlank()) return

        val impressions = songs
            .asSequence()
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .map { song ->
                RecommendationImpression(
                    songId = song.id,
                    surface = surface,
                    shownAt = timestampMs
                )
            }
            .toList()

        if (impressions.isEmpty()) return

        recommendationDao.insertImpressions(impressions)
        bumpRecommendationVersion()
    }

    private suspend fun loadRecommendationProfile(): RecommendationProfile {
        val onboardingCompleted = preferencesManager.onboardingCompleted.first()
        val languages = if (onboardingCompleted) {
            preferencesManager.preferredLanguages.first().filterNot { it.equals("None", ignoreCase = true) }.toSet()
        } else {
            emptySet()
        }
        val singers = if (onboardingCompleted) {
            preferencesManager.preferredSingers.first().filterNot { it.equals("None", ignoreCase = true) }.toSet()
        } else {
            emptySet()
        }
        val lyricists = if (onboardingCompleted) {
            preferencesManager.preferredLyricists.first().filterNot { it.equals("None", ignoreCase = true) }.toSet()
        } else {
            emptySet()
        }
        val musicDirectors = if (onboardingCompleted) {
            preferencesManager.preferredMusicDirectors.first().filterNot { it.equals("None", ignoreCase = true) }.toSet()
        } else {
            emptySet()
        }

        val recentQueries = recommendationDao.getRecentSearchQueries(limit = 40)
        val profileVector = featureEngineer.buildProfileVector(
            languages = languages,
            singers = singers,
            lyricists = lyricists,
            musicDirectors = musicDirectors,
            searchQueries = recentQueries
        )

        return RecommendationProfile(
            languages = languages,
            singers = singers,
            lyricists = lyricists,
            musicDirectors = musicDirectors,
            vector = profileVector
        )
    }

    private suspend fun buildBehaviorSignals(nowMs: Long): Map<String, SongBehaviorSignal> {
        val seedSongs = songDao.getPersonalizationSeedSongs(limit = 250)
        val metrics = recommendationDao.getSongBehaviorMetrics(sinceMs = nowMs - TRACKING_LOOKBACK_MS)

        val byId = seedSongs.associate { song ->
            song.id to SongBehaviorSignal(
                playCount = song.playCount,
                totalSessions = song.playCount,
                averageCompletionRate = if (song.playCount > 0) 0.7 else 0.0,
                totalListenedMs = 0,
                lastPlayedAt = song.lastPlayedAt
            )
        }.toMutableMap()

        metrics.forEach { metric ->
            val existing = byId[metric.songId] ?: SongBehaviorSignal()
            byId[metric.songId] = mergeBehavior(existing, metric)
        }

        return byId
    }

    private suspend fun buildInteractionSignals(nowMs: Long): Map<String, SongInteractionSignal> {
        val metrics = recommendationDao.getSongInteractionMetrics(sinceMs = nowMs - TRACKING_LOOKBACK_MS)
        return metrics.associate { metric -> metric.songId to metric.toDomainSignal() }
    }

    private fun mergeBehavior(existing: SongBehaviorSignal, metric: SongBehaviorMetrics): SongBehaviorSignal {
        val mergedLastPlayedAt = maxOfOrNull(existing.lastPlayedAt, metric.lastPlayedAt)
        return existing.copy(
            playCount = maxOf(existing.playCount, metric.totalSessions),
            totalSessions = metric.totalSessions,
            skipCount = metric.skipCount,
            completedCount = metric.completedCount,
            averageCompletionRate = metric.averageCompletionRate.coerceIn(0.0, 1.0),
            totalListenedMs = metric.totalListenedMs,
            lastPlayedAt = mergedLastPlayedAt
        )
    }

    private fun SongInteractionMetrics.toDomainSignal(): SongInteractionSignal {
        return SongInteractionSignal(
            likeCount = likeCount,
            unlikeCount = unlikeCount,
            replayCount = replayCount,
            playlistAddCount = playlistAddCount,
            lastInteractionAt = lastInteractionAt
        )
    }

    private fun rotateForRefresh(items: List<SongItem>, refreshNonce: Long): List<SongItem> {
        if (items.size <= 1) return items
        val pivot = (refreshNonce.absoluteValue % items.size).toInt()
        return items.drop(pivot) + items.take(pivot)
    }

    private fun buildHomeCacheKey(
        candidates: List<RecommendationCandidate>,
        topArtistWeights: Map<String, Int>,
        refreshNonce: Long
    ): String {
        val idsKey = candidates.joinToString(separator = "|") { it.song.id }
        val artistKey = topArtistWeights.entries
            .sortedByDescending { (_, weight) -> weight }
            .joinToString(separator = "|") { (artist, weight) -> "${artist.lowercase()}:$weight" }
        val nonceBucket = refreshNonce / 15_000L
        return "$idsKey#$artistKey#$nonceBucket"
    }

    private suspend fun cleanupTrackingDataIfNeeded(nowMs: Long) {
        if (nowMs - lastCleanupAtMs < CLEANUP_INTERVAL_MS) return
        lastCleanupAtMs = nowMs

        val cutoff = nowMs - TRACKING_RETENTION_MS
        recommendationDao.deleteOldListeningEvents(cutoff)
        recommendationDao.deleteOldInteractionSignals(cutoff)
        recommendationDao.deleteOldRecommendationImpressions(cutoff)
    }

    private fun bumpRecommendationVersion() {
        synchronized(cacheLock) {
            recommendationVersion += 1
            homeRecommendationCache = null
        }
    }

    private fun maxOfOrNull(first: Long?, second: Long?): Long? {
        return when {
            first == null -> second
            second == null -> first
            else -> maxOf(first, second)
        }
    }

    companion object {
        private const val TRACKING_LOOKBACK_MS = 90L * 24L * 60L * 60L * 1000L
        private const val TRACKING_RETENTION_MS = 120L * 24L * 60L * 60L * 1000L
        private const val RECENT_RECOMMENDATION_WINDOW_MS = 12L * 60L * 60L * 1000L
        private const val CLEANUP_INTERVAL_MS = 12L * 60L * 60L * 1000L
        private const val HOME_CACHE_TTL_MS = 2L * 60L * 1000L
    }
}
