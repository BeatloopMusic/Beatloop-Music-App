package com.beatloop.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beatloop.music.data.preferences.AudioQuality
import com.beatloop.music.data.preferences.PreferencesManager
import com.beatloop.music.data.preferences.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    // Appearance
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pureBlackEnabled: Boolean = false,
    val dynamicColorEnabled: Boolean = true,
    
    // Audio
    val audioQuality: AudioQuality = AudioQuality.HIGH,
    val normalizeAudioEnabled: Boolean = false,
    val skipSilenceEnabled: Boolean = false,
    
    // Playback
    val persistentQueueEnabled: Boolean = true,
    
    // SponsorBlock
    val sponsorBlockEnabled: Boolean = true,
    val skipIntroEnabled: Boolean = true,
    val skipOutroEnabled: Boolean = true,
    val skipSelfPromoEnabled: Boolean = true,
    val skipMusicOffTopicEnabled: Boolean = true,
    
    // Downloads
    val downloadQuality: AudioQuality = AudioQuality.VERY_HIGH,
    
    // Cache
    val maxCacheSizeMb: Int = 512,
    val currentCacheSizeMb: Int = 0
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        observePreferences()
    }
    
    private fun observePreferences() {
        viewModelScope.launch {
            combine(
                preferencesManager.themeMode,
                preferencesManager.pureBlackEnabled,
                preferencesManager.dynamicColorEnabled,
                preferencesManager.audioQuality,
                preferencesManager.normalizeAudioEnabled
            ) { theme, pureBlack, dynamicColor, quality, normalize ->
                _uiState.value.copy(
                    themeMode = theme,
                    pureBlackEnabled = pureBlack,
                    dynamicColorEnabled = dynamicColor,
                    audioQuality = quality,
                    normalizeAudioEnabled = normalize
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
        
        viewModelScope.launch {
            combine(
                preferencesManager.skipSilenceEnabled,
                preferencesManager.persistentQueueEnabled,
                preferencesManager.sponsorBlockEnabled
            ) { skipSilence, persistentQueue, sponsorBlock ->
                _uiState.value.copy(
                    skipSilenceEnabled = skipSilence,
                    persistentQueueEnabled = persistentQueue,
                    sponsorBlockEnabled = sponsorBlock
                )
            }.collect { state ->
                _uiState.update { 
                    it.copy(
                        skipSilenceEnabled = state.skipSilenceEnabled,
                        persistentQueueEnabled = state.persistentQueueEnabled,
                        sponsorBlockEnabled = state.sponsorBlockEnabled
                    )
                }
            }
        }
        
        viewModelScope.launch {
            combine(
                preferencesManager.skipIntroEnabled,
                preferencesManager.skipOutroEnabled,
                preferencesManager.skipSelfPromoEnabled,
                preferencesManager.skipMusicOffTopicEnabled
            ) { intro, outro, selfPromo, musicOffTopic ->
                _uiState.value.copy(
                    skipIntroEnabled = intro,
                    skipOutroEnabled = outro,
                    skipSelfPromoEnabled = selfPromo,
                    skipMusicOffTopicEnabled = musicOffTopic
                )
            }.collect { state ->
                _uiState.update {
                    it.copy(
                        skipIntroEnabled = state.skipIntroEnabled,
                        skipOutroEnabled = state.skipOutroEnabled,
                        skipSelfPromoEnabled = state.skipSelfPromoEnabled,
                        skipMusicOffTopicEnabled = state.skipMusicOffTopicEnabled
                    )
                }
            }
        }
        
        viewModelScope.launch {
            combine(
                preferencesManager.downloadQuality,
                preferencesManager.maxCacheSizeMb
            ) { downloadQuality, cacheSize ->
                _uiState.value.copy(
                    downloadQuality = downloadQuality,
                    maxCacheSizeMb = cacheSize
                )
            }.collect { state ->
                _uiState.update {
                    it.copy(
                        downloadQuality = state.downloadQuality,
                        maxCacheSizeMb = state.maxCacheSizeMb
                    )
                }
            }
        }
    }
    
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesManager.setThemeMode(mode)
        }
    }
    
    fun setPureBlackEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setPureBlackEnabled(enabled)
        }
    }
    
    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setDynamicColorEnabled(enabled)
        }
    }
    
    fun setAudioQuality(quality: AudioQuality) {
        viewModelScope.launch {
            preferencesManager.setAudioQuality(quality)
        }
    }
    
    fun setNormalizeAudioEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setNormalizeAudioEnabled(enabled)
        }
    }
    
    fun setSkipSilenceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setSkipSilenceEnabled(enabled)
        }
    }
    
    fun setPersistentQueueEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setPersistentQueueEnabled(enabled)
        }
    }
    
    fun setSponsorBlockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setSponsorBlockEnabled(enabled)
        }
    }
    
    fun setSkipIntroEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setSkipIntroEnabled(enabled)
        }
    }
    
    fun setSkipOutroEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setSkipOutroEnabled(enabled)
        }
    }
    
    fun setSkipSelfPromoEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setSkipSelfPromoEnabled(enabled)
        }
    }
    
    fun setSkipMusicOffTopicEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setSkipMusicOffTopicEnabled(enabled)
        }
    }
    
    fun setDownloadQuality(quality: AudioQuality) {
        viewModelScope.launch {
            preferencesManager.setDownloadQuality(quality)
        }
    }
    
    fun setMaxCacheSize(sizeMb: Int) {
        viewModelScope.launch {
            preferencesManager.setMaxCacheSizeMb(sizeMb)
        }
    }
    
    fun clearCache() {
        viewModelScope.launch {
            // Cache clearing logic would go here
            _uiState.update { it.copy(currentCacheSizeMb = 0) }
        }
    }
}
