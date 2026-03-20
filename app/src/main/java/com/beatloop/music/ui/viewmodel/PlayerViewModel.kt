package com.beatloop.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beatloop.music.data.model.Lyrics
import com.beatloop.music.data.model.SongItem
import com.beatloop.music.data.model.VideoVotes
import com.beatloop.music.data.repository.LyricsRepository
import com.beatloop.music.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val lyrics: Lyrics? = null,
    val isLoadingLyrics: Boolean = false,
    val lyricsError: String? = null,
    val isLiked: Boolean = false,
    val currentQueueIndex: Int = 0,
    val videoVotes: VideoVotes? = null,
    val isLoadingVideoVotes: Boolean = false,
    val videoVotesError: String? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val lyricsRepository: LyricsRepository,
    private val musicRepository: MusicRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    
    // Queue exposed as StateFlow
    private val _queue = MutableStateFlow<List<SongItem>>(emptyList())
    val queue: StateFlow<List<SongItem>> = _queue.asStateFlow()
    
    // Callback for queue item click (set by PlayerScreen)
    private var onPlayFromQueueCallback: ((Int) -> Unit)? = null
    
    fun setPlayFromQueueCallback(callback: (Int) -> Unit) {
        onPlayFromQueueCallback = callback
    }
    
    fun loadLyrics(songId: String, title: String, artist: String, durationSeconds: Int? = null) {
        if (title.isBlank()) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLyrics = true, lyricsError = null) }
            
            lyricsRepository.getLyrics(songId, title, artist, duration = durationSeconds)
                .onSuccess { lyrics ->
                    _uiState.update {
                        it.copy(
                            lyrics = lyrics,
                            isLoadingLyrics = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingLyrics = false,
                            lyricsError = error.message
                        )
                    }
                }
        }
    }
    
    fun toggleLike(songId: String) {
        viewModelScope.launch {
            val currentlyLiked = _uiState.value.isLiked
            _uiState.update { it.copy(isLiked = !currentlyLiked) }
            
            if (currentlyLiked) {
                musicRepository.unlikeSong(songId)
            } else {
                musicRepository.likeSong(songId)
            }
        }
    }
    
    fun checkIfLiked(songId: String) {
        viewModelScope.launch {
            val isLiked = musicRepository.isSongLiked(songId)
            _uiState.update { it.copy(isLiked = isLiked) }
        }
    }
    
    fun updateQueue(queue: List<SongItem>, currentIndex: Int) {
        _queue.value = queue
        _uiState.update {
            it.copy(
                currentQueueIndex = currentIndex
            )
        }
    }
    
    fun removeFromQueue(index: Int) {
        val currentQueue = _queue.value.toMutableList()
        if (index in currentQueue.indices) {
            currentQueue.removeAt(index)
            val newIndex = if (index < _uiState.value.currentQueueIndex) {
                _uiState.value.currentQueueIndex - 1
            } else {
                _uiState.value.currentQueueIndex
            }
            _uiState.update {
                it.copy(
                    currentQueueIndex = newIndex.coerceAtLeast(0)
                )
            }
            _queue.value = currentQueue
        }
    }
    
    fun playFromQueue(index: Int) {
        // Use callback to tell the player to seek
        onPlayFromQueueCallback?.invoke(index)
        _uiState.update { it.copy(currentQueueIndex = index) }
    }
    
    fun updateQueue(songs: List<SongItem>) {
        _queue.value = songs
    }

    fun loadVideoVotes(videoId: String) {
        if (videoId.isBlank()) {
            _uiState.update { it.copy(videoVotes = null, isLoadingVideoVotes = false, videoVotesError = null) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingVideoVotes = true, videoVotesError = null) }

            musicRepository.getVideoVotes(videoId)
                .onSuccess { votes ->
                    _uiState.update {
                        it.copy(
                            videoVotes = votes,
                            isLoadingVideoVotes = false,
                            videoVotesError = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            videoVotes = null,
                            isLoadingVideoVotes = false,
                            videoVotesError = error.message
                        )
                    }
                }
        }
    }
    
    fun clearLyrics() {
        _uiState.update {
            it.copy(
                lyrics = null,
                lyricsError = null,
                videoVotes = null,
                isLoadingVideoVotes = false,
                videoVotesError = null
            )
        }
    }
}
