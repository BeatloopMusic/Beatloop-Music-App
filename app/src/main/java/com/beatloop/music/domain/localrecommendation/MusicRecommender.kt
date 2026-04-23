package com.beatloop.music.domain.localrecommendation

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@Singleton
class MusicRecommender @Inject constructor() {

    // Builds a preference vector by weighting listens with duration, recency, and skip penalties.
    fun buildUserVector(history: List<UserHistory>, songs: List<Song>): FloatArray {
        if (history.isEmpty() || songs.isEmpty()) return FloatArray(FEATURE_VECTOR_SIZE)

        val songById = songs.associateBy { it.songId }
        val now = System.currentTimeMillis()
        val accumulator = FloatArray(FEATURE_VECTOR_SIZE)
        var totalWeight = 0f

        for (entry in history) {
            val song = songById[entry.songId] ?: continue
            val songFeatures = toSongFeatures(song = song, recencySignal = recencySignal(now, entry.timestamp), skipSignal = if (entry.skipped) 1f else 0f)
            val vector = songFeatures.toVector()

            val durationWeight = durationWeight(entry.listenDuration)
            val recencyWeight = 0.4f + (songFeatures.recencySignal * 0.6f)
            val skipWeight = if (entry.skipped) SKIP_MULTIPLIER else 1f
            val weight = durationWeight * recencyWeight * skipWeight
            if (weight <= 0f) continue

            for (i in accumulator.indices) {
                accumulator[i] += vector[i] * weight
            }
            totalWeight += weight
        }

        if (totalWeight > 0f) {
            for (i in accumulator.indices) {
                accumulator[i] /= totalWeight
            }
        }

        normalizeInPlace(accumulator)
        return accumulator
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        val size = min(a.size, b.size)
        if (size == 0) return 0f

        var dot = 0f
        var normA = 0f
        var normB = 0f

        for (i in 0 until size) {
            val av = a[i]
            val bv = b[i]
            dot += av * bv
            normA += av * av
            normB += bv * bv
        }

        if (normA <= EPSILON || normB <= EPSILON) return 0f
        return (dot / (sqrt(normA) * sqrt(normB))).coerceIn(-1f, 1f)
    }

    fun generateCandidates(
        allSongs: List<Song>,
        history: List<UserHistory>
    ): List<Song> {
        if (allSongs.isEmpty()) return emptyList()
        if (history.isEmpty()) {
            return allSongs
                .asSequence()
                .sortedByDescending { normalizePopularity(it.popularity) }
                .take(DEFAULT_CANDIDATE_POOL)
                .toList()
        }

        val playCountBySong = HashMap<String, Int>(history.size)
        val artistWeight = HashMap<String, Float>()
        val genreWeight = HashMap<String, Float>()
        val songById = allSongs.associateBy { it.songId }

        for (entry in history) {
            playCountBySong[entry.songId] = (playCountBySong[entry.songId] ?: 0) + 1
            val song = songById[entry.songId] ?: continue
            val weight = durationWeight(entry.listenDuration) * if (entry.skipped) SKIP_MULTIPLIER else 1f
            artistWeight[song.artist] = (artistWeight[song.artist] ?: 0f) + weight
            genreWeight[song.genre] = (genreWeight[song.genre] ?: 0f) + weight
        }

        val topArtists = artistWeight.entries
            .sortedByDescending { it.value }
            .take(4)
            .map { it.key }
            .toSet()

        val topGenres = genreWeight.entries
            .sortedByDescending { it.value }
            .take(4)
            .map { it.key }
            .toSet()

        val heavilyPlayed = playCountBySong
            .asSequence()
            .filter { (_, count) -> count >= HEAVY_PLAY_THRESHOLD }
            .map { (songId, _) -> songId }
            .toHashSet()

        val candidates = ArrayList<Song>(DEFAULT_CANDIDATE_POOL)
        for (song in allSongs) {
            if (heavilyPlayed.contains(song.songId)) continue

            val sameArtist = topArtists.contains(song.artist)
            val sameGenre = topGenres.contains(song.genre)
            val isPopular = normalizePopularity(song.popularity) >= POPULARITY_THRESHOLD

            if (sameArtist || sameGenre || isPopular) {
                candidates.add(song)
            }
        }

        if (candidates.size < MIN_CANDIDATE_POOL) {
            allSongs
                .asSequence()
                .filter { !heavilyPlayed.contains(it.songId) }
                .sortedByDescending { normalizePopularity(it.popularity) }
                .forEach { song ->
                    if (candidates.size >= DEFAULT_CANDIDATE_POOL) return@forEach
                    if (candidates.none { it.songId == song.songId }) {
                        candidates.add(song)
                    }
                }
        }

        return candidates
    }

    fun scoreSong(
        userVector: FloatArray,
        songVector: FloatArray,
        context: RecommendationContext
    ): Float {
        // Final score balances similarity with contextual boosts and behavior penalties.
        val similarity = cosineSimilarity(userVector, songVector)
        val recencyBoost = songVector.getOrElse(4) { 0f } * 0.15f
        val skipPenalty = songVector.getOrElse(5) { 0f } * 0.25f
        val popularityBoost = songVector.getOrElse(3) { 0f } * 0.10f

        val tempo = songVector.getOrElse(2) { 0f }
        val moodEnergy = songVector.getOrElse(1) { 0.5f }

        val morningBoost = if (context.hourOfDay in 5..11) {
            (tempo * 0.06f) + (moodEnergy * 0.05f)
        } else {
            0f
        }

        val nightCalmBoost = if (context.hourOfDay >= 22 || context.hourOfDay <= 4) {
            (1f - moodEnergy) * 0.09f
        } else {
            0f
        }

        val workoutBoost = if (context.isWorkout) {
            (tempo * 0.16f) + (moodEnergy * 0.12f)
        } else {
            0f
        }

        val focusBoost = if (context.isFocusMode) {
            val tempoComfort = 1f - abs(tempo - 0.45f)
            (tempoComfort * 0.08f) + ((1f - moodEnergy) * 0.05f)
        } else {
            0f
        }

        return (similarity * 0.65f) +
            popularityBoost +
            recencyBoost +
            morningBoost +
            nightCalmBoost +
            workoutBoost +
            focusBoost -
            skipPenalty
    }

    fun recommend(
        history: List<UserHistory>,
        songs: List<Song>,
        context: RecommendationContext
    ): List<RecommendationResult> {
        if (songs.isEmpty()) return emptyList()

        val userVector = buildUserVector(history, songs)
        val candidates = generateCandidates(songs, history)
        if (candidates.isEmpty()) return emptyList()

        // Precompute per-song skip/recency signals once before candidate scoring.
        val statsBySong = buildSongStats(history)
        val scored = ArrayList<RecommendationResult>(candidates.size)

        for (song in candidates) {
            val stats = statsBySong[song.songId]
            val recency = stats?.let { recencySignal(System.currentTimeMillis(), it.lastTimestamp) } ?: 0f
            val skipSignal = stats?.skipRate ?: 0f
            val songVector = toSongFeatures(song = song, recencySignal = recency, skipSignal = skipSignal).toVector()
            val score = scoreSong(userVector = userVector, songVector = songVector, context = context)
            scored.add(RecommendationResult(song = song, score = score))
        }

        scored.sortByDescending { it.score }
        return scored.take(context.topN)
    }

    fun getSessionBasedRecommendations(
        recentSongs: List<Song>,
        allSongs: List<Song>
    ): List<Song> {
        if (recentSongs.isEmpty() || allSongs.isEmpty()) return emptyList()

        val sessionSeed = recentSongs.takeLast(SESSION_SEED_SIZE)
        val sessionVector = FloatArray(FEATURE_VECTOR_SIZE)

        for (song in sessionSeed) {
            val vector = toSongFeatures(song = song, recencySignal = 1f, skipSignal = 0f).toVector()
            for (i in sessionVector.indices) {
                sessionVector[i] += vector[i]
            }
        }

        for (i in sessionVector.indices) {
            sessionVector[i] /= sessionSeed.size.toFloat()
        }
        normalizeInPlace(sessionVector)

        val recentIds = sessionSeed.mapTo(HashSet()) { it.songId }
        return allSongs
            .asSequence()
            .filter { !recentIds.contains(it.songId) }
            .map { song ->
                val vector = toSongFeatures(song = song, recencySignal = 0f, skipSignal = 0f).toVector()
                val continuityBoost = continuityBoost(song, sessionSeed)
                song to (cosineSimilarity(sessionVector, vector) + continuityBoost)
            }
            .sortedByDescending { it.second }
            .take(DEFAULT_SESSION_TOP_N)
            .map { it.first }
            .toList()
    }

    private fun continuityBoost(song: Song, sessionSeed: List<Song>): Float {
        var boost = 0f
        for (seed in sessionSeed) {
            if (seed.artist == song.artist) boost += 0.12f
            if (seed.genre == song.genre) boost += 0.08f
        }
        return min(boost, 0.32f)
    }

    private fun buildSongStats(history: List<UserHistory>): Map<String, SongStats> {
        if (history.isEmpty()) return emptyMap()

        val stats = HashMap<String, SongStatsMutable>()
        for (entry in history) {
            val mutable = stats.getOrPut(entry.songId) { SongStatsMutable() }
            mutable.count += 1
            if (entry.skipped) mutable.skips += 1
            if (entry.timestamp > mutable.lastTimestamp) mutable.lastTimestamp = entry.timestamp
        }

        return stats.mapValues { (_, value) ->
            val skipRate = if (value.count == 0) 0f else value.skips.toFloat() / value.count.toFloat()
            SongStats(lastTimestamp = value.lastTimestamp, skipRate = skipRate.coerceIn(0f, 1f))
        }
    }

    private fun toSongFeatures(song: Song, recencySignal: Float, skipSignal: Float): SongFeatures {
        return SongFeatures(
            genreSignal = categorySignal(song.genre),
            moodEnergy = moodEnergy(song.mood),
            tempoNormalized = normalizeTempo(song.tempo),
            popularityNormalized = normalizePopularity(song.popularity),
            recencySignal = recencySignal.coerceIn(0f, 1f),
            skipSignal = skipSignal.coerceIn(0f, 1f)
        )
    }

    private fun normalizeTempo(tempo: Float): Float {
        if (tempo <= 0f) return DEFAULT_TEMPO
        return (tempo / MAX_EXPECTED_TEMPO).coerceIn(0f, 1f)
    }

    private fun normalizePopularity(popularity: Float): Float {
        return if (popularity > 1f) {
            (popularity / 100f).coerceIn(0f, 1f)
        } else {
            popularity.coerceIn(0f, 1f)
        }
    }

    private fun moodEnergy(mood: String): Float {
        val normalized = mood.lowercase()
        return when {
            normalized.contains("workout") || normalized.contains("party") || normalized.contains("dance") -> 0.95f
            normalized.contains("happy") || normalized.contains("upbeat") || normalized.contains("energetic") -> 0.8f
            normalized.contains("focus") || normalized.contains("study") || normalized.contains("instrumental") -> 0.45f
            normalized.contains("calm") || normalized.contains("chill") || normalized.contains("lofi") -> 0.3f
            normalized.contains("sad") || normalized.contains("sleep") -> 0.2f
            else -> 0.55f
        }
    }

    private fun categorySignal(value: String): Float {
        val hash = value.lowercase().hashCode().toLong() and 0x7fffffff
        return (hash % 1000L).toFloat() / 1000f
    }

    private fun recencySignal(now: Long, timestamp: Long): Float {
        val ageMillis = max(0L, now - timestamp)
        val ageHours = ageMillis.toFloat() / 3_600_000f
        return exp(-ageHours / 72f).coerceIn(0f, 1f)
    }

    private fun durationWeight(listenDuration: Long): Float {
        if (listenDuration <= 0L) return 0.15f
        val normalized = (listenDuration.toFloat() / 240_000f).coerceIn(0.15f, 1f)
        return normalized
    }

    private fun normalizeInPlace(vector: FloatArray) {
        var normSquared = 0f
        for (value in vector) {
            normSquared += value * value
        }

        if (normSquared <= EPSILON) return

        val invNorm = 1f / sqrt(normSquared)
        for (i in vector.indices) {
            vector[i] *= invNorm
        }
    }

    private data class SongStatsMutable(
        var count: Int = 0,
        var skips: Int = 0,
        var lastTimestamp: Long = 0
    )

    private data class SongStats(
        val lastTimestamp: Long,
        val skipRate: Float
    )

    companion object {
        private const val FEATURE_VECTOR_SIZE = 6
        private const val SESSION_SEED_SIZE = 5
        private const val DEFAULT_SESSION_TOP_N = 20
        private const val DEFAULT_CANDIDATE_POOL = 240
        private const val MIN_CANDIDATE_POOL = 64
        private const val HEAVY_PLAY_THRESHOLD = 6
        private const val POPULARITY_THRESHOLD = 0.78f
        private const val SKIP_MULTIPLIER = 0.45f
        private const val MAX_EXPECTED_TEMPO = 220f
        private const val DEFAULT_TEMPO = 0.5f
        private const val EPSILON = 1e-6f
    }
}
