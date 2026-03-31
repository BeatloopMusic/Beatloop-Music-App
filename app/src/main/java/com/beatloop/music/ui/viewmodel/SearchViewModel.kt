package com.beatloop.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beatloop.music.data.database.SearchHistory
import com.beatloop.music.data.model.AlbumItem
import com.beatloop.music.data.model.ArtistItem
import com.beatloop.music.data.model.PlaylistItem
import com.beatloop.music.data.model.SearchFilter
import com.beatloop.music.data.model.SongItem
import com.beatloop.music.data.model.VideoItem
import com.beatloop.music.domain.usecase.search.ClearSearchHistoryUseCase
import com.beatloop.music.domain.usecase.search.DeleteSearchHistoryUseCase
import com.beatloop.music.domain.usecase.search.GetSearchHistoryUseCase
import com.beatloop.music.domain.usecase.search.GetSearchSuggestionsUseCase
import com.beatloop.music.domain.usecase.search.SearchContentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false,
    val query: String = "",
    val songs: List<SongItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val videos: List<VideoItem> = emptyList(),
    val suggestions: List<String> = emptyList()
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchContentUseCase: SearchContentUseCase,
    private val getSearchSuggestionsUseCase: GetSearchSuggestionsUseCase,
    private val getSearchHistoryUseCase: GetSearchHistoryUseCase,
    private val deleteSearchHistoryUseCase: DeleteSearchHistoryUseCase,
    private val clearSearchHistoryUseCase: ClearSearchHistoryUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    val searchHistory: StateFlow<List<SearchHistory>> = getSearchHistoryUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    fun search(query: String, filter: SearchFilter = SearchFilter.All) {
        if (query.isBlank()) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, query = query) }
            
            searchContentUseCase(query, filter)
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            hasSearched = true,
                            songs = result.songs,
                            artists = result.artists,
                            albums = result.albums,
                            playlists = result.playlists,
                            videos = result.videos,
                            suggestions = emptyList()
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            hasSearched = true,
                            error = error.message ?: "Search failed"
                        )
                    }
                }
        }
    }
    
    fun getSearchSuggestions(query: String) {
        viewModelScope.launch {
            getSearchSuggestionsUseCase(query)
                .onSuccess { suggestions ->
                    _uiState.update { it.copy(suggestions = suggestions) }
                }
        }
    }
    
    fun clearSearchHistory() {
        viewModelScope.launch {
            clearSearchHistoryUseCase()
        }
    }
    
    fun deleteSearchHistory(query: String) {
        viewModelScope.launch {
            deleteSearchHistoryUseCase(query)
        }
    }
    
    fun clearSearch() {
        _uiState.update {
            SearchUiState()
        }
    }
}
