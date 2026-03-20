package com.beatloop.music.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.beatloop.music.data.model.LocalPlaylist
import com.beatloop.music.data.model.LocalPlaylistWithSongs
import com.beatloop.music.data.model.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: PlaylistEntity): Long
    
    @Update
    suspend fun update(playlist: PlaylistEntity)
    
    @Delete
    suspend fun delete(playlist: PlaylistEntity)
    
    @Query("SELECT * FROM local_playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): PlaylistEntity?
    
    @Query("SELECT * FROM local_playlists WHERE id = :id")
    fun getPlaylistByIdFlow(id: Long): Flow<PlaylistEntity?>
    
    @Query("""
        SELECT lp.id, lp.name, 
        (SELECT COUNT(*) FROM local_playlist_songs WHERE playlistId = lp.id) as songCount
        FROM local_playlists lp
        ORDER BY lp.createdAt DESC
    """)
    fun getAllPlaylists(): Flow<List<LocalPlaylist>>
    
    @Query("SELECT COUNT(*) FROM local_playlists")
    fun getPlaylistsCount(): Flow<Int>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(crossRef: PlaylistSongCrossRef)
    
    @Query("DELETE FROM local_playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removePlaylistSong(playlistId: Long, songId: String)
    
    @Query("DELETE FROM local_playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: Long)
    
    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN local_playlist_songs lps ON s.id = lps.songId
        WHERE lps.playlistId = :playlistId
        ORDER BY lps.addedAt DESC
    """)
    fun getPlaylistSongs(playlistId: Long): Flow<List<Song>>
    
    @Query("SELECT COUNT(*) FROM local_playlist_songs WHERE playlistId = :playlistId")
    fun getPlaylistSongCount(playlistId: Long): Flow<Int>

    @Transaction
    @Query("SELECT * FROM local_playlists WHERE id = :playlistId")
    fun getPlaylistWithSongs(playlistId: Long): Flow<LocalPlaylistWithSongs?>
    
    @Transaction
    suspend fun addSongToPlaylist(playlistId: Long, songId: String) {
        insertPlaylistSong(PlaylistSongCrossRef(playlistId, songId))
    }
    
    @Query("UPDATE local_playlists SET name = :name WHERE id = :id")
    suspend fun updatePlaylistName(id: Long, name: String)
}
