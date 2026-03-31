package com.beatloop.music.download

import android.content.Context
import androidx.lifecycle.Observer
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.beatloop.music.data.model.SongItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class DownloadProgressInfo(
        val state: WorkInfo.State,
        val progress: Int? = null,
        val downloadedBytes: Long? = null,
        val totalBytes: Long? = null,
        val fileSizeBytes: Long? = null
    )

    private val workManager = WorkManager.getInstance(context)
    
    fun download(song: SongItem, downloadVideo: Boolean = false, videoQuality: Int = 360) {
        val workRequest = DownloadWorker.createWorkRequest(
            songId = song.id,
            title = song.title,
            artist = song.artistsText,
            thumbnailUrl = song.thumbnailUrl,
            downloadVideo = downloadVideo,
            videoQuality = videoQuality
        )

        workManager.enqueueUniqueWork(
            "download_${song.id}",
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }
    
    fun download(
        songId: String,
        title: String,
        artist: String,
        thumbnailUrl: String?,
        downloadVideo: Boolean = false,
        videoQuality: Int = 360
    ) {
        val workRequest = DownloadWorker.createWorkRequest(
            songId = songId,
            title = title,
            artist = artist,
            thumbnailUrl = thumbnailUrl,
            downloadVideo = downloadVideo,
            videoQuality = videoQuality
        )

        workManager.enqueueUniqueWork(
            "download_$songId",
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }
    
    fun cancelDownload(songId: String) {
        workManager.cancelAllWorkByTag("download_$songId")
    }
    
    fun cancelAllDownloads() {
        workManager.cancelAllWork()
    }

    fun observeDownload(songId: String): Flow<DownloadProgressInfo?> {
        val tag = "download_$songId"
        return callbackFlow {
            val source = workManager.getWorkInfosByTagLiveData(tag)
            val observer = Observer<List<WorkInfo>> { infos ->
                val info = infos
                    .sortedByDescending { it.runAttemptCount }
                    .firstOrNull()

                if (info == null) {
                    trySend(null)
                    return@Observer
                }

                val progress = info.progress.getInt(DownloadWorker.KEY_PROGRESS, -1)
                    .takeIf { it >= 0 }
                    ?: info.outputData.getInt(DownloadWorker.KEY_PROGRESS, -1).takeIf { it >= 0 }

                val downloadedBytes = info.progress.getLong(DownloadWorker.KEY_DOWNLOADED_BYTES, -1L)
                    .takeIf { it >= 0L }

                val totalBytes = info.progress.getLong(DownloadWorker.KEY_TOTAL_BYTES, -1L)
                    .takeIf { it > 0L }

                val fileSizeBytes = info.outputData.getLong(DownloadWorker.KEY_FILE_SIZE, -1L)
                    .takeIf { it > 0L }

                trySend(
                    DownloadProgressInfo(
                        state = info.state,
                        progress = progress,
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                        fileSizeBytes = fileSizeBytes
                    )
                )
            }

            source.observeForever(observer)
            awaitClose { source.removeObserver(observer) }
        }.distinctUntilChanged()
    }
    
    fun getDownloadProgress(songId: String) = workManager.getWorkInfosByTagLiveData("download_$songId")
}
