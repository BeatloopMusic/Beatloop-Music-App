package com.beatloop.music.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(searchHistory: SearchHistory)
    
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    fun getSearchHistory(limit: Int = 20): Flow<List<SearchHistory>>
    
    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun delete(query: String)
    
    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}

@Dao
interface PlayHistoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(playHistory: PlayHistory)
    
    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN play_history ph ON s.id = ph.songId
        GROUP BY s.id
        ORDER BY MAX(ph.playedAt) DESC
        LIMIT :limit
    """)
    fun getPlayHistory(limit: Int = 100): Flow<List<com.beatloop.music.data.model.Song>>
    
    @Query("DELETE FROM play_history WHERE playedAt < :before")
    suspend fun deleteOldEntries(before: Long)
    
    @Query("DELETE FROM play_history")
    suspend fun clearAll()
    
    @Query("SELECT COUNT(DISTINCT songId) FROM play_history")
    fun getPlayHistoryCount(): Flow<Int>
}
