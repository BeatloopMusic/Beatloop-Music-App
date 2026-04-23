package com.beatloop.music.domain.localrecommendation

import org.junit.Assert.assertTrue
import org.junit.Test

class MusicRecommenderTest {

    private val recommender = MusicRecommender()

    @Test
    fun cosineSimilarity_returnsNearOne_forIdenticalVectors() {
        val a = floatArrayOf(0.2f, 0.5f, 0.7f, 0.1f, 0f, 0f)
        val score = recommender.cosineSimilarity(a, a)
        assertTrue(score > 0.999f)
    }

    @Test
    fun scoreSong_boostsEnergeticSong_duringWorkout() {
        val user = floatArrayOf(0.3f, 0.7f, 0.8f, 0.6f, 0.3f, 0f)
        val energetic = floatArrayOf(0.2f, 0.9f, 0.95f, 0.7f, 0.2f, 0.05f)
        val calm = floatArrayOf(0.2f, 0.2f, 0.25f, 0.7f, 0.2f, 0.05f)

        val workoutContext = RecommendationContext(
            hourOfDay = 8,
            isWorkout = true,
            isFocusMode = false
        )

        val energeticScore = recommender.scoreSong(user, energetic, workoutContext)
        val calmScore = recommender.scoreSong(user, calm, workoutContext)

        assertTrue(energeticScore > calmScore)
    }

    @Test
    fun recommend_ranksArtistAndGenreContinuityHigher() {
        val now = System.currentTimeMillis()

        val history = listOf(
            UserHistory(songId = "s1", timestamp = now - 30_000, skipped = false, listenDuration = 210_000),
            UserHistory(songId = "s2", timestamp = now - 120_000, skipped = false, listenDuration = 180_000),
            UserHistory(songId = "s3", timestamp = now - 240_000, skipped = true, listenDuration = 15_000)
        )

        val songs = listOf(
            Song("s1", "Track A", "Artist A", "Pop", 128f, "upbeat", 0.84f),
            Song("s2", "Track B", "Artist A", "Pop", 124f, "happy", 0.80f),
            Song("s3", "Track C", "Artist X", "Metal", 160f, "aggressive", 0.60f),
            Song("s4", "Track D", "Artist A", "Pop", 122f, "upbeat", 0.74f),
            Song("s5", "Track E", "Artist Y", "Ambient", 70f, "calm", 0.77f)
        )

        val context = RecommendationContext(hourOfDay = 9, isWorkout = false, isFocusMode = false, topN = 3)
        val result = recommender.recommend(history = history, songs = songs, context = context)

        assertTrue(result.isNotEmpty())
        val topIds = result.take(3).map { it.song.songId }.toSet()
        assertTrue(topIds.contains("s4") || topIds.contains("s2"))
    }
}
