package com.beatloop.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beatloop.music.data.model.AlbumItem
import com.beatloop.music.data.model.ArtistItem
import com.beatloop.music.data.model.SongItem
import com.beatloop.music.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val artist: ArtistItem? = null,
    val description: String? = null,
    val topSongs: List<SongItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val singles: List<AlbumItem> = emptyList()
)

@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ArtistUiState())
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()
    
    fun loadArtist(artistId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            musicRepository.getArtist(artistId)
                .onSuccess { artistPage ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            artist = artistPage.artist,
                            description = artistPage.description,
                            topSongs = artistPage.songs,
                            albums = artistPage.albums,
                            singles = artistPage.singles
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load artist"
                        )
                    }
                }
        }
    }
}
