package com.beatloop.music.ui.viewmodel

import android.content.Intent
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beatloop.music.data.model.DownloadState
import com.beatloop.music.data.model.LocalPlaylist
import com.beatloop.music.data.model.PlaylistItem
import com.beatloop.music.data.model.SongItem
import com.beatloop.music.data.repository.MusicRepository
import com.beatloop.music.download.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared ViewModel for song actions like like/unlike, add to playlist, etc.
 * Can be used across multiple screens.
 */
@HiltViewModel
class SongActionsViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val downloadManager: DownloadManager
) : ViewModel() {

    data class DownloadSongInfo(
        val title: String,
        val artist: String,
        val thumbnailUrl: String?
    )

    data class DownloadUiState(
        val state: DownloadState = DownloadState.NOT_DOWNLOADED,
        val progress: Int? = null,
        val downloadedBytes: Long? = null,
        val totalBytes: Long? = null,
        val fileSizeBytes: Long? = null
    )
    
    val playlists: StateFlow<List<LocalPlaylist>> = musicRepository.getLocalPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    private val _likedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val likedSongIds: StateFlow<Set<String>> = _likedSongIds.asStateFlow()

    private val _downloadedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedSongIds: StateFlow<Set<String>> = _downloadedSongIds.asStateFlow()

    private val _downloadUiStateMap = MutableStateFlow<Map<String, DownloadUiState>>(emptyMap())
    val downloadUiStateMap: StateFlow<Map<String, DownloadUiState>> = _downloadUiStateMap.asStateFlow()

    private val _activeDownloadSongInfo = MutableStateFlow<Map<String, DownloadSongInfo>>(emptyMap())
    val activeDownloadSongInfo: StateFlow<Map<String, DownloadSongInfo>> = _activeDownloadSongInfo.asStateFlow()

    private val downloadObservers = mutableMapOf<String, Job>()

    private val _uiEvent = MutableSharedFlow<String>()
    val uiEvent = _uiEvent.asSharedFlow()
    
    init {
        // Track liked song IDs for quick lookup
        viewModelScope.launch {
            musicRepository.getLikedSongs().collect { songs ->
                _likedSongIds.value = songs.map { it.id }.toSet()
            }
        }

        viewModelScope.launch {
            musicRepository.getDownloadedSongs().collect { songs ->
                val ids = songs.map { it.id }.toSet()
                _downloadedSongIds.value = ids
                _downloadUiStateMap.update { current ->
                    val mutable = current.toMutableMap()
                    ids.forEach { id ->
                        val previous = mutable[id]
                        mutable[id] = (previous ?: DownloadUiState()).copy(state = DownloadState.DOWNLOADED)
                    }
                    mutable
                }
            }
        }
    }
    
    fun isSongLiked(songId: String): Boolean = _likedSongIds.value.contains(songId)
    
    fun toggleLike(song: SongItem) {
        viewModelScope.launch {
            if (isSongLiked(song.id)) {
                musicRepository.unlikeSong(song.id)
            } else {
                musicRepository.likeSong(song)
            }
        }
    }
    
    fun likeSong(song: SongItem) {
        viewModelScope.launch {
            musicRepository.likeSong(song)
        }
    }
    
    fun unlikeSong(songId: String) {
        viewModelScope.launch {
            musicRepository.unlikeSong(songId)
        }
    }
    
    fun addToPlaylist(playlistId: Long, song: SongItem, context: Context) {
        viewModelScope.launch {
            val added = musicRepository.addSongToPlaylist(playlistId, song)     
            if (added) {
                Toast.makeText(context, "Added to playlist", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Already in playlist", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    fun createPlaylistAndAddSong(playlistName: String, song: SongItem, context: Context) {
        viewModelScope.launch {
            val playlistId = musicRepository.createLocalPlaylist(playlistName)
            musicRepository.addSongToPlaylist(playlistId, song)
            Toast.makeText(context, "Created playlist and added song", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveRemotePlaylist(playlist: PlaylistItem, context: Context) {
        viewModelScope.launch {
            musicRepository.saveRemotePlaylist(playlist)
                .onSuccess { addedSongs ->
                    val message = if (addedSongs > 0) {
                        "Saved ${playlist.title} ($addedSongs songs)"
                    } else {
                        "Playlist is already up to date"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    Toast.makeText(
                        context,
                        error.message ?: "Failed to save playlist",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
    
    fun addToPlayHistory(song: SongItem) {
        viewModelScope.launch {
            musicRepository.addToPlayHistory(song)
        }
    }

    fun downloadSong(song: SongItem, downloadVideo: Boolean = false, videoQuality: Int = 360) {
        viewModelScope.launch {
            musicRepository.saveSong(song)
            musicRepository.setSongDownloadState(song.id, DownloadState.DOWNLOADING)
            _downloadUiStateMap.update {
                it + (song.id to DownloadUiState(state = DownloadState.DOWNLOADING, progress = 0, downloadedBytes = 0L))
            }
            _activeDownloadSongInfo.update {
                it + (song.id to DownloadSongInfo(song.title, song.artistsText, song.thumbnailUrl))
            }
            downloadManager.download(song, downloadVideo = downloadVideo, videoQuality = videoQuality)
            observeDownload(song.id)
        }
    }

    fun cancelDownload(songId: String) {
        downloadManager.cancelDownload(songId)
        _downloadUiStateMap.update {
            it + (songId to DownloadUiState(state = DownloadState.NOT_DOWNLOADED, progress = null))
        }
        _activeDownloadSongInfo.update { it - songId }
    }

    fun observeDownload(songId: String) {
        if (downloadObservers.containsKey(songId)) return

        val job = viewModelScope.launch {
            downloadManager.observeDownload(songId).collect { info ->
                if (info == null) return@collect

                val mappedState = when (info.state) {
                    androidx.work.WorkInfo.State.SUCCEEDED -> DownloadState.DOWNLOADED
                    androidx.work.WorkInfo.State.FAILED -> DownloadState.FAILED
                    androidx.work.WorkInfo.State.CANCELLED -> DownloadState.NOT_DOWNLOADED
                    androidx.work.WorkInfo.State.ENQUEUED,
                    androidx.work.WorkInfo.State.RUNNING,
                    androidx.work.WorkInfo.State.BLOCKED -> DownloadState.DOWNLOADING
                }

                _downloadUiStateMap.update { current ->
                    current + (
                        songId to DownloadUiState(
                            state = mappedState,
                            progress = info.progress,
                            downloadedBytes = info.downloadedBytes,
                            totalBytes = info.totalBytes,
                            fileSizeBytes = info.fileSizeBytes
                        )
                    )
                }

                if (mappedState != DownloadState.DOWNLOADING) {
                    downloadObservers.remove(songId)?.cancel()
                    _activeDownloadSongInfo.update { it - songId }
                }
            }
        }

        downloadObservers[songId] = job
    }
    
    fun createShareIntent(song: SongItem): Intent {
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Check out \"${song.title}\" by ${song.artistsText} on YouTube Music: https://music.youtube.com/watch?v=${song.id}")
            },
            "Share song"
        )
    }
}
