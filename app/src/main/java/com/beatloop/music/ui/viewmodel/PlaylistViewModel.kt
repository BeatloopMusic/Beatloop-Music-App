package com.beatloop.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beatloop.music.data.model.PlaylistItem
import com.beatloop.music.data.model.SongItem
import com.beatloop.music.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val playlist: PlaylistItem? = null,
    val songs: List<SongItem> = emptyList(),
    val hasContinuation: Boolean = false,
    val continuation: String? = null
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()
    
    private var currentPlaylistId: String = ""
    
    fun loadPlaylist(playlistId: String) {
        currentPlaylistId = playlistId
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            musicRepository.getPlaylist(playlistId)
                .onSuccess { playlistPage ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            playlist = playlistPage.playlist,
                            songs = playlistPage.songs,
                            hasContinuation = playlistPage.continuation != null,
                            continuation = playlistPage.continuation
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load playlist"
                        )
                    }
                }
        }
    }
    
    fun loadMore() {
        val continuation = _uiState.value.continuation ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            
            musicRepository.getPlaylistContinuation(currentPlaylistId, continuation)
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            songs = it.songs + result.songs,
                            hasContinuation = result.continuation != null,
                            continuation = result.continuation
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoadingMore = false)
                    }
                }
        }
    }
}
