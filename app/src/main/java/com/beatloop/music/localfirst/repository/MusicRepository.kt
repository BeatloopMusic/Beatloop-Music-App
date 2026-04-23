package com.beatloop.music.localfirst.repository

import com.beatloop.music.data.database.LocalRecommendationDao
import com.beatloop.music.data.database.SongEntity
import com.beatloop.music.data.database.UserHistoryEntity
import com.beatloop.music.domain.localrecommendation.MusicRecommender
import com.beatloop.music.domain.localrecommendation.RecommendationContext
import com.beatloop.music.domain.localrecommendation.RecommendationResult
import com.beatloop.music.domain.localrecommendation.Song
import com.beatloop.music.domain.localrecommendation.UserHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val localRecommendationDao: LocalRecommendationDao,
    private val musicRecommender: MusicRecommender
) {

    private val cachedUserVector = AtomicReference<FloatArray?>(null)
    private val songVectorCache = ConcurrentHashMap<String, FloatArray>()

    fun observeRecommendations(
        context: RecommendationContext,
        historyLimit: Int = DEFAULT_HISTORY_LIMIT
    ): Flow<List<RecommendationResult>> {
        return combine(
            localRecommendationDao.observeRecentHistory(limit = historyLimit),
            localRecommendationDao.observeAllSongs()
        ) { historyEntities, songEntities ->
            computeRecommendations(
                history = historyEntities.map { it.toDomainModel() },
                songs = songEntities.map { it.toDomainModel() },
                context = context
            )
        }.flowOn(Dispatchers.Default)
    }

    suspend fun upsertSongs(songs: List<SongEntity>) {
        if (songs.isEmpty()) return
        localRecommendationDao.insertSongs(songs)
    }

    suspend fun recordSongCompletion(songId: String, listenDuration: Long, timestamp: Long = System.currentTimeMillis()) {
        localRecommendationDao.insertHistory(
            UserHistoryEntity(
                songId = songId,
                timestamp = timestamp,
                skipped = false,
                listenDuration = listenDuration.coerceAtLeast(0)
            )
        )
    }

    suspend fun recordSongSkip(songId: String, listenDuration: Long, timestamp: Long = System.currentTimeMillis()) {
        localRecommendationDao.insertHistory(
            UserHistoryEntity(
                songId = songId,
                timestamp = timestamp,
                skipped = true,
                listenDuration = listenDuration.coerceAtLeast(0)
            )
        )
    }

    suspend fun getSessionBasedRecommendations(limit: Int = 20): List<Song> {
        return withContext(Dispatchers.Default) {
            val history = localRecommendationDao.getRecentHistory(DEFAULT_HISTORY_LIMIT)
            val songs = localRecommendationDao.getAllSongs()
            val domainSongs = songs.map { it.toDomainModel() }
            val songById = domainSongs.associateBy { it.songId }
            val recentSessionSongs = history
                .asSequence()
                .mapNotNull { event -> songById[event.songId] }
                .distinctBy { it.songId }
                .take(5)
                .toList()

            musicRecommender
                .getSessionBasedRecommendations(recentSongs = recentSessionSongs, allSongs = domainSongs)
                .take(limit)
        }
    }

    private fun computeRecommendations(
        history: List<UserHistory>,
        songs: List<Song>,
        context: RecommendationContext
    ): List<RecommendationResult> {
        if (songs.isEmpty()) return emptyList()

        // Cache warm-up for vectors used repeatedly in recommendation loops.
        songs.forEach { song ->
            songVectorCache.getOrPut(song.songId) {
                floatArrayOf(
                    song.tempo,
                    song.popularity,
                    song.genre.hashCode().toFloat(),
                    song.mood.hashCode().toFloat()
                )
            }
        }

        val userVector = musicRecommender.buildUserVector(history = history, songs = songs)
        cachedUserVector.set(userVector)

        return musicRecommender.recommend(history = history, songs = songs, context = context)
    }

    private fun UserHistoryEntity.toDomainModel(): UserHistory {
        return UserHistory(
            songId = songId,
            timestamp = timestamp,
            skipped = skipped,
            listenDuration = listenDuration
        )
    }

    private fun SongEntity.toDomainModel(): Song {
        return Song(
            songId = songId,
            title = title,
            artist = artist,
            genre = genre,
            tempo = tempo,
            mood = mood,
            popularity = popularity
        )
    }

    companion object {
        private const val DEFAULT_HISTORY_LIMIT = 200
    }
}
