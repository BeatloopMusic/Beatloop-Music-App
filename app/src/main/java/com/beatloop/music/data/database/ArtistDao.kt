package com.beatloop.music.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.beatloop.music.data.model.Artist
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(artist: Artist)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(artists: List<Artist>)
    
    @Update
    suspend fun update(artist: Artist)
    
    @Delete
    suspend fun delete(artist: Artist)
    
    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun getArtistById(id: String): Artist?
    
    @Query("SELECT * FROM artists WHERE id = :id")
    fun getArtistByIdFlow(id: String): Flow<Artist?>
    
    @Query("SELECT * FROM artists WHERE followed = 1 ORDER BY followedAt DESC")
    fun getFollowedArtists(): Flow<List<Artist>>
    
    @Query("SELECT COUNT(*) FROM artists WHERE followed = 1")
    fun getFollowedArtistsCount(): Flow<Int>
    
    @Query("UPDATE artists SET followed = :followed, followedAt = :followedAt WHERE id = :id")
    suspend fun updateFollowed(id: String, followed: Boolean, followedAt: Long?)
    
    @Query("SELECT * FROM artists ORDER BY name ASC")
    fun getAllArtists(): Flow<List<Artist>>
}
