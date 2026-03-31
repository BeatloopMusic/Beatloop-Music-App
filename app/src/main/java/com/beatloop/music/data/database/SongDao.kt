package com.beatloop.music.data.database

import androidx.room.*
import com.beatloop.music.data.model.DownloadState
import com.beatloop.music.data.model.LocalPlaylist
import com.beatloop.music.data.model.LocalPlaylistWithSongs
import com.beatloop.music.data.model.Song
import com.beatloop.music.data.model.SongItem
import kotlinx.coroutines.flow.Flow

data class ArtistPlayStat(
    val artistName: String,
    val totalPlays: Long
)

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: Song)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<Song>)
    
    @Update
    suspend fun update(song: Song)
    
    @Delete
    suspend fun delete(song: Song)
    
    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: String): Song?
    
    @Query("SELECT * FROM songs WHERE id = :id")
    fun getSongByIdFlow(id: String): Flow<Song?>
    
    @Query("SELECT * FROM songs WHERE liked = 1 ORDER BY likedAt DESC")
    fun getLikedSongsEntities(): Flow<List<Song>>
    
    @Query("SELECT COUNT(*) FROM songs WHERE liked = 1")
    fun getLikedSongsCount(): Flow<Int>
    
    @Query("SELECT * FROM songs WHERE downloadState = :state")
    fun getSongsByDownloadState(state: DownloadState): Flow<List<Song>>
    
    @Query("SELECT * FROM songs WHERE downloadState = 'DOWNLOADED' ORDER BY title ASC")
    fun getDownloadedSongsEntities(): Flow<List<Song>>
    
    @Query("SELECT COUNT(*) FROM songs WHERE downloadState = 'DOWNLOADED'")
    fun getDownloadedSongsCount(): Flow<Int>
    
    @Query("UPDATE songs SET liked = :liked, likedAt = :likedAt WHERE id = :id")
    suspend fun updateLiked(id: String, liked: Boolean, likedAt: Long?)
    
    @Query("UPDATE songs SET downloadState = :state, localPath = :localPath WHERE id = :id")
    suspend fun updateDownloadState(id: String, state: DownloadState, localPath: String?)
    
    @Query(
        """
        UPDATE songs
        SET playCount = playCount + 1,
            lastPlayedAt = :playedAt,
            lastUpdatedTimestamp = :playedAt,
            isSynced = 0
        WHERE id = :id
        """
    )
    suspend fun incrementPlayCount(id: String, playedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM songs WHERE playCount > 0 AND isSynced = 0 ORDER BY lastUpdatedTimestamp ASC LIMIT :limit")
    suspend fun getUnsyncedListeningSongs(limit: Int = 200): List<Song>

    @Query("SELECT MAX(lastUpdatedTimestamp) FROM songs WHERE playCount > 0")
    suspend fun getLatestListeningUpdatedTimestamp(): Long?

    @Query("UPDATE songs SET isSynced = 1 WHERE id IN (:songIds)")
    suspend fun markListeningSongsSynced(songIds: List<String>)

    @Query(
        """
        UPDATE songs
        SET title = :title,
            artistsText = :artistsText,
            playCount = :playCount,
            lastPlayedAt = :lastPlayedAt,
            lastUpdatedTimestamp = :lastUpdatedTimestamp,
            isSynced = :isSynced
        WHERE id = :songId
        """
    )
    suspend fun updateListeningAggregate(
        songId: String,
        title: String,
        artistsText: String,
        playCount: Int,
        lastPlayedAt: Long?,
        lastUpdatedTimestamp: Long,
        isSynced: Boolean
    )
    
    @Query("SELECT * FROM songs ORDER BY playCount DESC LIMIT :limit")
    fun getMostPlayedSongs(limit: Int = 50): Flow<List<Song>>
    
    @Query("SELECT * FROM songs WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun getRecentlyPlayedSongs(limit: Int = 50): Flow<List<Song>>

    @Query("""
        SELECT id, title, artistsText, thumbnailUrl, albumId, duration, localPath
        FROM songs
        WHERE lastPlayedAt IS NOT NULL
        ORDER BY lastPlayedAt DESC
        LIMIT :limit
    """)
    suspend fun getRecentlyPlayedSongItems(limit: Int = 30): List<SongItem>

    @Query("""
        SELECT * FROM songs
        WHERE playCount > 0 OR liked = 1
        ORDER BY (playCount * 4) + CASE WHEN liked = 1 THEN 12 ELSE 0 END DESC,
                 COALESCE(lastPlayedAt, 0) DESC
        LIMIT :limit
    """)
    suspend fun getPersonalizationSeedSongs(limit: Int = 150): List<Song>

    @Query("""
        SELECT artistsText as artistName, SUM(playCount) as totalPlays
        FROM songs
        WHERE playCount > 0
        GROUP BY artistsText
        ORDER BY totalPlays DESC
        LIMIT :limit
    """)
    suspend fun getTopArtistPlayStats(limit: Int = 30): List<ArtistPlayStat>
    
    @Query("DELETE FROM songs WHERE downloadState != 'DOWNLOADED' AND liked = 0")
    suspend fun cleanupUnusedSongs()
    
    // Like/Unlike methods for SongItem compatibility
    @Query("UPDATE songs SET liked = 1, likedAt = :timestamp WHERE id = :songId")
    suspend fun likeSong(songId: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE songs SET liked = 0, likedAt = NULL WHERE id = :songId")
    suspend fun unlikeSong(songId: String)
    
    @Query("SELECT liked FROM songs WHERE id = :songId")
    suspend fun isSongLiked(songId: String): Boolean
    
    // Playlist methods
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createPlaylist(playlist: PlaylistEntity): Long

    @Query("SELECT * FROM local_playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistEntityById(playlistId: Long): PlaylistEntity?

    @Query("SELECT * FROM local_playlists WHERE syncId = :syncId LIMIT 1")
    suspend fun getPlaylistBySyncId(syncId: String): PlaylistEntity?

    @Query(
        """
        SELECT lp.id as id,
               lp.syncId as syncId,
               lp.name as name,
               COALESCE(GROUP_CONCAT(lps.songId, ','), '') as songIdsCsv,
               lp.lastUpdatedTimestamp as lastUpdatedTimestamp,
               lp.isSynced as isSynced
        FROM local_playlists lp
        LEFT JOIN local_playlist_songs lps ON lps.playlistId = lp.id
        WHERE lp.isSynced = 0
        GROUP BY lp.id
        ORDER BY lp.lastUpdatedTimestamp ASC
        LIMIT :limit
        """
    )
    suspend fun getUnsyncedPlaylists(limit: Int = 100): List<PlaylistSyncSnapshot>

    @Query("SELECT MAX(lastUpdatedTimestamp) FROM local_playlists")
    suspend fun getLatestPlaylistUpdatedTimestamp(): Long?

    @Query("UPDATE local_playlists SET isSynced = 1 WHERE syncId IN (:syncIds)")
    suspend fun markPlaylistsSynced(syncIds: List<String>)

    @Query("UPDATE local_playlists SET lastUpdatedTimestamp = :updatedAt, isSynced = 0 WHERE id = :playlistId")
    suspend fun markPlaylistDirty(playlistId: Long, updatedAt: Long = System.currentTimeMillis())
    
    @Query("SELECT id, name, (SELECT COUNT(*) FROM local_playlist_songs WHERE playlistId = local_playlists.id) as songCount FROM local_playlists ORDER BY createdAt DESC")
    fun getLocalPlaylists(): Flow<List<LocalPlaylist>>
    
    @Query("DELETE FROM local_playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)
    
    @Query("UPDATE local_playlists SET name = :name, lastUpdatedTimestamp = :updatedAt, isSynced = 0 WHERE id = :playlistId")
    suspend fun updatePlaylistName(
        playlistId: Long,
        name: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE local_playlists SET name = :name, lastUpdatedTimestamp = :updatedAt, isSynced = 1 WHERE id = :playlistId")
    suspend fun applyRemotePlaylistUpdate(
        playlistId: Long,
        name: String,
        updatedAt: Long
    )
    
    @Transaction
    @Query("SELECT * FROM local_playlists WHERE id = :playlistId")
    fun getPlaylistWithSongs(playlistId: Long): Flow<LocalPlaylistWithSongs?>
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylistEntry(entry: PlaylistSongCrossRef): Long

    suspend fun addSongToPlaylist(playlistId: Long, songId: String): Boolean {
        val inserted = addSongToPlaylistEntry(PlaylistSongCrossRef(playlistId, songId)) != -1L
        if (inserted) {
            markPlaylistDirty(playlistId)
        }
        return inserted
    }

    @Query("DELETE FROM local_playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylistEntry(playlistId: Long, songId: String)

    @Query("DELETE FROM local_playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: Long)

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        removeSongFromPlaylistEntry(playlistId, songId)
        markPlaylistDirty(playlistId)
    }
    
    // Downloads - returns as SongItem
    @Query("""
        SELECT id, title, artist as artistsText, thumbnailUrl, NULL as albumId, NULL as duration, filePath as localPath
        FROM downloaded_songs ORDER BY downloadedAt DESC
    """)
    fun getDownloadedSongs(): Flow<List<SongItem>>
    
    @Query("DELETE FROM downloaded_songs WHERE id = :songId")
    suspend fun deleteDownload(songId: String)
    
    // Play History
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToPlayHistory(entry: PlayHistoryEntry)
    
    @Query("""
        SELECT s.id, s.title, s.artistsText, s.thumbnailUrl, NULL as albumId, NULL as duration, s.localPath as localPath
        FROM play_history_entries ph
        INNER JOIN songs s ON ph.songId = s.id
        ORDER BY ph.playedAt DESC
        LIMIT 100
    """)
    fun getPlayHistory(): Flow<List<SongItem>>
    
    @Query("DELETE FROM play_history_entries")
    suspend fun clearPlayHistory()
    
    // Liked songs as SongItem
    @Query("""
        SELECT id, title, artistsText, thumbnailUrl, albumId, NULL as duration, localPath
        FROM songs WHERE liked = 1 ORDER BY likedAt DESC
    """)
    fun getLikedSongs(): Flow<List<SongItem>>
}
