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
        const val KEY_PROGRESS = "progress"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_FILE_SIZE = "file_size"
        const val KEY_DOWNLOAD_VIDEO = "download_video"
        const val KEY_VIDEO_QUALITY = "video_quality"
        
        const val CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID = 2000
        
        fun createWorkRequest(
            songId: String,
            title: String,
            artist: String,
            thumbnailUrl: String?,
            downloadVideo: Boolean = false,
            videoQuality: Int = 360
        ): OneTimeWorkRequest {
            val data = workDataOf(
                KEY_SONG_ID to songId,
                KEY_SONG_TITLE to title,
                KEY_SONG_ARTIST to artist,
                KEY_THUMBNAIL_URL to thumbnailUrl,
                KEY_DOWNLOAD_VIDEO to downloadVideo,
                KEY_VIDEO_QUALITY to videoQuality
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
        val downloadVideo = inputData.getBoolean(KEY_DOWNLOAD_VIDEO, false)
        val videoQuality = inputData.getInt(KEY_VIDEO_QUALITY, 360).let {
            when (it) {
                144, 240, 360, 480, 720 -> it
                else -> 360
            }
        }

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
            setForeground(createForegroundInfo(title, progress = 0, downloadedBytes = 0L, totalBytes = null))
            
            // Get stream URL
            val streamUrl = if (downloadVideo) {
                musicRepository.getVideoStreamUrl(songId, targetVideoHeight = videoQuality).getOrNull()
                    ?: musicRepository.getStreamUrl(songId).getOrNull()
            } else {
                musicRepository.getStreamUrl(songId).getOrNull()
            }
                ?: run {
                    database.songDao().updateDownloadState(songId, DownloadState.FAILED, null)
                    return@withContext Result.failure()
                }
            
            // Create downloads directory
            val downloadsDir = File(context.filesDir, "downloads")
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            
            // Download the audio file with progress updates.
            val mediaFile = File(downloadsDir, if (downloadVideo) "$songId.mp4" else "$songId.m4a")
            downloadFile(streamUrl, mediaFile) { downloadedBytes, totalBytes ->
                val progress = if (totalBytes > 0L) {
                    ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                } else {
                    0
                }

                setProgressAsync(
                    workDataOf(
                        KEY_PROGRESS to progress,
                        KEY_DOWNLOADED_BYTES to downloadedBytes,
                        KEY_TOTAL_BYTES to totalBytes
                    )
                )
                setForegroundAsync(createForegroundInfo(title, progress, downloadedBytes, totalBytes))
            }
            
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
                filePath = mediaFile.absolutePath,
                thumbnailPath = thumbnailPath,
                downloadedAt = System.currentTimeMillis(),
                fileSize = mediaFile.length()
            )
            
            database.downloadedSongDao().insert(downloadedSong)
            database.songDao().updateDownloadState(songId, DownloadState.DOWNLOADED, mediaFile.absolutePath)

            setProgressAsync(
                workDataOf(
                    KEY_PROGRESS to 100,
                    KEY_DOWNLOADED_BYTES to mediaFile.length(),
                    KEY_TOTAL_BYTES to mediaFile.length(),
                    KEY_FILE_SIZE to mediaFile.length()
                )
            )
            
            // Show completion notification
            showCompletionNotification(title)
            
            Result.success(workDataOf(KEY_FILE_SIZE to mediaFile.length(), KEY_PROGRESS to 100))
        } catch (e: Exception) {
            e.printStackTrace()
            database.songDao().updateDownloadState(songId, DownloadState.FAILED, null)
            setProgressAsync(workDataOf(KEY_PROGRESS to 0))
            Result.retry()
        }
    }
    
    private suspend fun downloadFile(
        url: String,
        destination: File,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ) {
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

                        onProgress?.invoke(totalBytesRead, contentLength)
                    }
                }
            }
        }
    }
    
    private fun createForegroundInfo(
        songTitle: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long?
    ): ForegroundInfo {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val progressText = if (totalBytes != null && totalBytes > 0L) {
            "${progress.coerceIn(0, 100)}% • ${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}"
        } else {
            "Preparing download..."
        }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Downloading")
            .setContentText("$songTitle • $progressText")
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), totalBytes == null || totalBytes <= 0L)
            .setContentIntent(pendingIntent)
            .build()
        
        return ForegroundInfo(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
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

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format("%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }
}
