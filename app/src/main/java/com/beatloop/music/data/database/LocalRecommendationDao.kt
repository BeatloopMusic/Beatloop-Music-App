package com.beatloop.music.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalRecommendationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: UserHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbedding(embedding: SongEmbeddingEntity)

    @Query("SELECT * FROM user_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int): List<UserHistoryEntity>

    @Query("SELECT * FROM recommendation_songs")
    suspend fun getAllSongs(): List<SongEntity>

    @Query("SELECT * FROM recommendation_songs WHERE artist = :artist")
    suspend fun getSongsByArtist(artist: String): List<SongEntity>

    @Query("SELECT * FROM recommendation_songs WHERE genre = :genre")
    suspend fun getSongsByGenre(genre: String): List<SongEntity>

    @Query("SELECT * FROM user_history ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentHistory(limit: Int): Flow<List<UserHistoryEntity>>

    @Query("SELECT * FROM recommendation_songs")
    fun observeAllSongs(): Flow<List<SongEntity>>
}
