package com.beatloop.music.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "downloaded_songs")
data class DownloadedSong(
    @PrimaryKey
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val filePath: String,
    val thumbnailPath: String?,
    val downloadedAt: Long,
    val fileSize: Long
)

@Dao
interface DownloadedSongDao {
    @Query("SELECT * FROM downloaded_songs ORDER BY downloadedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadedSong>>
    
    @Query("SELECT * FROM downloaded_songs WHERE id = :songId")
    suspend fun getDownload(songId: String): DownloadedSong?
    
    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_songs WHERE id = :songId)")
    suspend fun isDownloaded(songId: String): Boolean
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadedSong)
    
    @Delete
    suspend fun delete(download: DownloadedSong)
    
    @Query("DELETE FROM downloaded_songs WHERE id = :songId")
    suspend fun deleteById(songId: String)
    
    @Query("DELETE FROM downloaded_songs")
    suspend fun deleteAll()
    
    @Query("SELECT SUM(fileSize) FROM downloaded_songs")
    suspend fun getTotalDownloadSize(): Long?
}
