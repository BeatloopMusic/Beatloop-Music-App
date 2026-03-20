package com.beatloop.music.download

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.beatloop.music.data.model.SongItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)
    
    fun download(song: SongItem) {
        val workRequest = DownloadWorker.createWorkRequest(
            songId = song.id,
            title = song.title,
            artist = song.artistsText,
            thumbnailUrl = song.thumbnailUrl
        )

        workManager.enqueueUniqueWork(
            "download_${song.id}",
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }
    
    fun download(songId: String, title: String, artist: String, thumbnailUrl: String?) {
        val workRequest = DownloadWorker.createWorkRequest(
            songId = songId,
            title = title,
            artist = artist,
            thumbnailUrl = thumbnailUrl
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
    
    fun getDownloadProgress(songId: String) = workManager.getWorkInfosByTagLiveData("download_$songId")
}
