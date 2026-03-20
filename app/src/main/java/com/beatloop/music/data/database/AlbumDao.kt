package com.beatloop.music.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.beatloop.music.data.model.Album
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(album: Album)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(albums: List<Album>)
    
    @Update
    suspend fun update(album: Album)
    
    @Delete
    suspend fun delete(album: Album)
    
    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getAlbumById(id: String): Album?
    
    @Query("SELECT * FROM albums WHERE id = :id")
    fun getAlbumByIdFlow(id: String): Flow<Album?>
    
    @Query("SELECT * FROM albums WHERE liked = 1 ORDER BY likedAt DESC")
    fun getLikedAlbums(): Flow<List<Album>>
    
    @Query("SELECT COUNT(*) FROM albums WHERE liked = 1")
    fun getLikedAlbumsCount(): Flow<Int>
    
    @Query("UPDATE albums SET liked = :liked, likedAt = :likedAt WHERE id = :id")
    suspend fun updateLiked(id: String, liked: Boolean, likedAt: Long?)
    
    @Query("SELECT * FROM albums WHERE artistId = :artistId ORDER BY year DESC")
    fun getAlbumsByArtist(artistId: String): Flow<List<Album>>
    
    @Query("SELECT * FROM albums ORDER BY title ASC")
    fun getAllAlbums(): Flow<List<Album>>
}
