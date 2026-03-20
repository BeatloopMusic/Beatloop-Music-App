package com.beatloop.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beatloop.music.data.model.LocalPlaylist
import com.beatloop.music.data.model.SongItem
import com.beatloop.music.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocalPlaylistUiState(
    val isLoading: Boolean = false,
    val playlist: LocalPlaylist? = null,
    val songs: List<SongItem> = emptyList()
)

@HiltViewModel
class LocalPlaylistViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LocalPlaylistUiState())
    val uiState: StateFlow<LocalPlaylistUiState> = _uiState.asStateFlow()
    
    private var currentPlaylistId: Long = 0
    
    fun loadPlaylist(playlistId: Long) {
        currentPlaylistId = playlistId
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            musicRepository.getLocalPlaylistWithSongs(playlistId).collect { playlistWithSongs ->
                _uiState.update {
                    val localPlaylist = playlistWithSongs?.playlist?.let { entity ->
                        LocalPlaylist(
                            id = entity.id,
                            name = entity.name,
                            songCount = playlistWithSongs.songs.size
                        )
                    }
                    val songItems = playlistWithSongs?.songs?.map { song ->
                        SongItem(
                            id = song.id,
                            title = song.title,
                            artistsText = song.artistsText,
                            thumbnailUrl = song.thumbnailUrl,
                            albumId = song.albumId,
                            duration = song.duration
                        )
                    } ?: emptyList()
                    
                    it.copy(
                        isLoading = false,
                        playlist = localPlaylist,
                        songs = songItems
                    )
                }
            }
        }
    }
    
    fun renamePlaylist(name: String) {
        viewModelScope.launch {
            musicRepository.renameLocalPlaylist(currentPlaylistId, name)
        }
    }
    
    fun deletePlaylist() {
        viewModelScope.launch {
            musicRepository.deleteLocalPlaylist(currentPlaylistId)
        }
    }
    
    fun removeSongFromPlaylist(songId: String) {
        viewModelScope.launch {
            musicRepository.removeSongFromPlaylist(currentPlaylistId, songId)
        }
    }
}
