package com.beatloop.music.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beatloop.music.data.model.DownloadState
import com.beatloop.music.data.model.LocalPlaylist
import com.beatloop.music.data.model.SongItem
import com.beatloop.music.data.repository.MusicRepository
import com.beatloop.music.download.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
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
    
    val playlists: StateFlow<List<LocalPlaylist>> = musicRepository.getLocalPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    private val _likedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val likedSongIds: StateFlow<Set<String>> = _likedSongIds.asStateFlow()
    
    init {
        // Track liked song IDs for quick lookup
        viewModelScope.launch {
            musicRepository.getLikedSongs().collect { songs ->
                _likedSongIds.value = songs.map { it.id }.toSet()
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
    
    fun addToPlaylist(playlistId: Long, song: SongItem) {
        viewModelScope.launch {
            musicRepository.addSongToPlaylist(playlistId, song)
        }
    }
    
    fun createPlaylistAndAddSong(playlistName: String, song: SongItem) {
        viewModelScope.launch {
            musicRepository.createLocalPlaylist(playlistName)
            // Get the new playlist and add song to it
            musicRepository.getLocalPlaylists().first().firstOrNull { it.name == playlistName }?.let {
                musicRepository.addSongToPlaylist(it.id, song)
            }
        }
    }
    
    fun addToPlayHistory(song: SongItem) {
        viewModelScope.launch {
            musicRepository.addToPlayHistory(song)
        }
    }

    fun downloadSong(song: SongItem) {
        viewModelScope.launch {
            musicRepository.saveSong(song)
            musicRepository.setSongDownloadState(song.id, DownloadState.DOWNLOADING)
            downloadManager.download(song)
        }
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
