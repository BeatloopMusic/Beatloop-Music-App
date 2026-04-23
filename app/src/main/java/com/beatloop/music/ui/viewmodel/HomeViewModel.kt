package com.beatloop.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beatloop.music.data.model.AlbumItem
import com.beatloop.music.data.model.GenreRecommendationSection
import com.beatloop.music.data.model.MoodGenreItem
import com.beatloop.music.data.model.PlaylistItem
import com.beatloop.music.data.model.SongItem
import com.beatloop.music.domain.usecase.home.GetHomeContentUseCase
import com.beatloop.music.utils.NetworkConnectivityObserver
import com.beatloop.music.utils.NetworkStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val greeting: String = "",
    val motivationMessage: String? = null,
    val quickPicks: List<SongItem> = emptyList(),
    val personalizedRecommendations: List<SongItem> = emptyList(),
    val recentlyPlayed: List<SongItem> = emptyList(),
    val topArtists: List<String> = emptyList(),
    val trendingSongs: List<SongItem> = emptyList(),
    val genreSections: List<GenreRecommendationSection> = emptyList(),
    val moodsAndGenres: List<MoodGenreItem> = emptyList(),
    val newReleases: List<AlbumItem> = emptyList(),
    val recommendedPlaylists: List<PlaylistItem> = emptyList(),
    val networkStatus: NetworkStatus = NetworkStatus.Available,
    val showNetworkMessage: Boolean = false,
    val wasOffline: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getHomeContentUseCase: GetHomeContentUseCase,
    private val networkConnectivityObserver: NetworkConnectivityObserver
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var networkMessageJob: Job? = null
    private var retryWhenOnlineJob: Job? = null
    
    init {
        _uiState.update { it.copy(greeting = getGreeting()) }
        observeNetworkStatus()
        loadHome(forceRefresh = false)
    }
    
    private fun observeNetworkStatus() {
        viewModelScope.launch {
            networkConnectivityObserver.observe().collect { status ->
                val wasOffline = _uiState.value.networkStatus == NetworkStatus.Lost || 
                                 _uiState.value.networkStatus == NetworkStatus.Unavailable
                val isNowOnline = status == NetworkStatus.Available
                
                _uiState.update { 
                    it.copy(
                        networkStatus = status,
                        showNetworkMessage = true,
                        wasOffline = if (isNowOnline && wasOffline) true else it.wasOffline
                    ) 
                }
                
                // If we just came back online and have no content, reload
                if (isNowOnline && wasOffline && _uiState.value.quickPicks.isEmpty()) {
                    retryWhenOnlineJob?.cancel()
                    loadHome(forceRefresh = true)
                }

                networkMessageJob?.cancel()
                networkMessageJob = viewModelScope.launch {
                    delay(3000)
                    _uiState.update { it.copy(showNetworkMessage = false, wasOffline = false) }
                }
            }
        }
    }

    fun onHomeVisible() {
        loadHome(forceRefresh = false)
    }
    
    fun loadHome(forceRefresh: Boolean = false) {
        if (_uiState.value.isLoading) return

        val hasCachedContent = _uiState.value.quickPicks.isNotEmpty() ||
            _uiState.value.personalizedRecommendations.isNotEmpty() ||
            _uiState.value.recentlyPlayed.isNotEmpty() ||
            _uiState.value.trendingSongs.isNotEmpty() ||
            _uiState.value.genreSections.isNotEmpty() ||
            _uiState.value.moodsAndGenres.isNotEmpty() ||
            _uiState.value.newReleases.isNotEmpty() ||
            _uiState.value.recommendedPlaylists.isNotEmpty()

        if (!forceRefresh && hasCachedContent) return

        // Check network before loading
        if (!networkConnectivityObserver.isNetworkAvailable()) {
            _uiState.update { 
                it.copy(
                    isLoading = false, 
                    error = "No internet connection. Please check your network settings.",
                    networkStatus = NetworkStatus.Unavailable,
                    showNetworkMessage = true
                ) 
            }
            scheduleRetryUntilNetworkAvailable()
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val refreshNonce = System.currentTimeMillis()
            
            getHomeContentUseCase(refreshNonce)
                .onSuccess { homeContent ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            greeting = homeContent.greeting.ifEmpty { getGreeting() },
                            motivationMessage = homeContent.motivationMessage,
                            quickPicks = homeContent.quickPicks,
                            personalizedRecommendations = homeContent.personalizedRecommendations,
                            recentlyPlayed = homeContent.recentlyPlayed,
                            topArtists = homeContent.topArtists,
                            trendingSongs = homeContent.trendingSongs,
                            genreSections = homeContent.genreSections,
                            moodsAndGenres = homeContent.moodsAndGenres,
                            newReleases = homeContent.newReleases,
                            recommendedPlaylists = homeContent.recommendedPlaylists
                        )
                    }
                }
                .onFailure { error ->
                    val offlineNow = !networkConnectivityObserver.isNetworkAvailable()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = if (offlineNow) {
                                "No internet connection. Retrying automatically when network is back."
                            } else {
                                error.message ?: "Failed to load content. Please try again."
                            }
                        )
                    }

                    if (offlineNow) {
                        scheduleRetryUntilNetworkAvailable()
                    }
                }
        }
    }

    private fun scheduleRetryUntilNetworkAvailable() {
        if (retryWhenOnlineJob?.isActive == true) return

        retryWhenOnlineJob = viewModelScope.launch {
            while (true) {
                if (networkConnectivityObserver.isNetworkAvailable()) {
                    loadHome(forceRefresh = true)
                    break
                }
                delay(2000)
            }
        }
    }
    
    fun dismissNetworkMessage() {
        _uiState.update { it.copy(showNetworkMessage = false) }
    }
    
    private fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Good morning"
            hour < 18 -> "Good afternoon"
            else -> "Good evening"
        }
    }
}
