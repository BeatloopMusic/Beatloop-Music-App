package com.beatloop.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beatloop.music.data.model.LocalPlaylist
import com.beatloop.music.data.model.SongItem
import com.beatloop.music.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val playlists: List<LocalPlaylist> = emptyList(),
    val downloads: List<SongItem> = emptyList(),
    val playHistory: List<SongItem> = emptyList(),
    val likedSongs: List<SongItem> = emptyList()
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    
    // Separate state flows for screens that need them
    val downloads: StateFlow<List<SongItem>> = musicRepository.getDownloadedSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val history: StateFlow<List<SongItem>> = musicRepository.getPlayHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val likedSongs: StateFlow<List<SongItem>> = musicRepository.getLikedSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    init {
        loadLibraryContent()
    }
    
    private fun loadLibraryContent() {
        viewModelScope.launch {
            // Combine all flows
            combine(
                musicRepository.getLocalPlaylists(),
                musicRepository.getDownloadedSongs(),
                musicRepository.getPlayHistory(),
                musicRepository.getLikedSongs()
            ) { playlists, downloads, history, liked ->
                LibraryUiState(
                    playlists = playlists,
                    downloads = downloads,
                    playHistory = history,
                    likedSongs = liked
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
    
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            musicRepository.createLocalPlaylist(name)
        }
    }
    
    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            musicRepository.deleteLocalPlaylist(playlistId)
        }
    }
    
    fun deleteDownload(songId: String) {
        viewModelScope.launch {
            musicRepository.deleteDownload(songId)
        }
    }
    
    fun clearHistory() {
        viewModelScope.launch {
            musicRepository.clearPlayHistory()
        }
    }
    
    fun unlikeSong(songId: String) {
        viewModelScope.launch {
            musicRepository.unlikeSong(songId)
        }
    }
}
