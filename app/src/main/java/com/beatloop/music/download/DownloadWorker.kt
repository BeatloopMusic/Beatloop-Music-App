package com.beatloop.music.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.beatloop.music.MainActivity
import com.beatloop.music.R
import com.beatloop.music.data.database.BeatloopDatabase
import com.beatloop.music.data.database.DownloadedSong
import com.beatloop.music.data.model.DownloadState
import com.beatloop.music.data.model.Song
import com.beatloop.music.data.repository.MusicRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val musicRepository: MusicRepository,
    private val database: BeatloopDatabase,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(context, params) {
    
    companion object {
        const val KEY_SONG_ID = "song_id"
        const val KEY_SONG_TITLE = "song_title"
        const val KEY_SONG_ARTIST = "song_artist"
        const val KEY_THUMBNAIL_URL = "thumbnail_url"
        
        const val CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID = 2000
        
        fun createWorkRequest(
            songId: String,
            title: String,
            artist: String,
            thumbnailUrl: String?
        ): OneTimeWorkRequest {
            val data = workDataOf(
                KEY_SONG_ID to songId,
                KEY_SONG_TITLE to title,
                KEY_SONG_ARTIST to artist,
                KEY_THUMBNAIL_URL to thumbnailUrl
            )
            
            return OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag("download_$songId")
                .build()
        }
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val songId = inputData.getString(KEY_SONG_ID) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_SONG_TITLE) ?: "Unknown"
        val artist = inputData.getString(KEY_SONG_ARTIST) ?: "Unknown"
        val thumbnailUrl = inputData.getString(KEY_THUMBNAIL_URL)

        // Ensure the song row exists and mark download as started.
        val existingSong = database.songDao().getSongById(songId)
        if (existingSong == null) {
            database.songDao().insert(
                Song(
                    id = songId,
                    title = title,
                    artistsText = artist,
                    thumbnailUrl = thumbnailUrl,
                    downloadState = DownloadState.DOWNLOADING
                )
            )
        } else {
            database.songDao().updateDownloadState(songId, DownloadState.DOWNLOADING, existingSong.localPath)
        }
        
        try {
            // Show progress notification
            setForeground(createForegroundInfo(title))
            
            // Get stream URL
            val streamUrl = musicRepository.getStreamUrl(songId).getOrNull()
                ?: run {
                    database.songDao().updateDownloadState(songId, DownloadState.FAILED, null)
                    return@withContext Result.failure()
                }
            
            // Create downloads directory
            val downloadsDir = File(context.filesDir, "downloads")
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            
            // Download the audio file
            val audioFile = File(downloadsDir, "$songId.m4a")
            downloadFile(streamUrl, audioFile)
            
            // Download thumbnail
            var thumbnailPath: String? = null
            if (thumbnailUrl != null) {
                val thumbnailFile = File(downloadsDir, "${songId}_thumb.jpg")
                try {
                    downloadFile(thumbnailUrl, thumbnailFile)
                    thumbnailPath = thumbnailFile.absolutePath
                } catch (e: Exception) {
                    // Thumbnail download failed, continue without it
                }
            }
            
            // Save to database
            val downloadedSong = DownloadedSong(
                id = songId,
                title = title,
                artist = artist,
                thumbnailUrl = thumbnailUrl,
                filePath = audioFile.absolutePath,
                thumbnailPath = thumbnailPath,
                downloadedAt = System.currentTimeMillis(),
                fileSize = audioFile.length()
            )
            
            database.downloadedSongDao().insert(downloadedSong)
            database.songDao().updateDownloadState(songId, DownloadState.DOWNLOADED, audioFile.absolutePath)
            
            // Show completion notification
            showCompletionNotification(title)
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            database.songDao().updateDownloadState(songId, DownloadState.FAILED, null)
            Result.retry()
        }
    }
    
    private suspend fun downloadFile(url: String, destination: File) {
        val request = Request.Builder()
            .url(url)
            .build()
        
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Download failed: ${response.code}")
            }
            
            val body = response.body ?: throw Exception("Empty response body")
            val contentLength = body.contentLength()
            
            FileOutputStream(destination).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        // Update progress
                        if (contentLength > 0) {
                            val progress = (totalBytesRead * 100 / contentLength).toInt()
                            setProgressAsync(workDataOf("progress" to progress))
                        }
                    }
                }
            }
        }
    }
    
    private fun createForegroundInfo(songTitle: String): ForegroundInfo {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Downloading")
            .setContentText(songTitle)
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .setContentIntent(pendingIntent)
            .build()
        
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }
    
    private fun showCompletionNotification(songTitle: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText(songTitle)
            .setSmallIcon(R.drawable.ic_download_done)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(
                UUID.randomUUID().hashCode(),
                notification
            )
        } catch (e: SecurityException) {
            // Handle notification permission not granted
        }
    }
}
