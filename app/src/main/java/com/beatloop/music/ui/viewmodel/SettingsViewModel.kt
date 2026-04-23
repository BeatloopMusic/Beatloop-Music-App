package com.beatloop.music.ui.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beatloop.music.data.auth.AuthManager
import com.beatloop.music.data.auth.IdentityMode
import com.beatloop.music.data.preferences.AudioQuality
import com.beatloop.music.data.preferences.PreferencesManager
import com.beatloop.music.data.preferences.ThemeMode
import com.beatloop.music.domain.recommendation.RecommendationContentRules
import com.beatloop.music.sync.SyncManager
import com.beatloop.music.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val videoPlaybackQuality: Int = 360,

    // Personalization
    val contentLanguage: String = "English",
    val contentCountry: String = "US",
    val preferredLanguages: Set<String> = setOf("English"),

    // Cache
    val maxCacheSizeMb: Int = 512,
    val currentCacheSizeMb: Int = 0,

    // Identity and sync
    val currentUserId: String = "",
    val identityModeLabel: String = "Local",
    val cloudSyncAvailable: Boolean = false,
    val isIdentityActionInProgress: Boolean = false,
    val statusMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val authManager: AuthManager,
    private val syncManager: SyncManager,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observePreferences()
        refreshIdentityState()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferencesManager.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            preferencesManager.pureBlackEnabled.collect { enabled ->
                _uiState.update { it.copy(pureBlackEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            preferencesManager.dynamicColorEnabled.collect { enabled ->
                _uiState.update { it.copy(dynamicColorEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            preferencesManager.audioQuality.collect { quality ->
                _uiState.update { it.copy(audioQuality = quality) }
            }
        }
        viewModelScope.launch {
            preferencesManager.normalizeAudioEnabled.collect { enabled ->
                _uiState.update { it.copy(normalizeAudioEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            preferencesManager.skipSilenceEnabled.collect { enabled ->
                _uiState.update { it.copy(skipSilenceEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            preferencesManager.persistentQueueEnabled.collect { enabled ->
                _uiState.update { it.copy(persistentQueueEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            preferencesManager.sponsorBlockEnabled.collect { enabled ->
                _uiState.update { it.copy(sponsorBlockEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            preferencesManager.skipIntroEnabled.collect { enabled ->
                _uiState.update { it.copy(skipIntroEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            preferencesManager.skipOutroEnabled.collect { enabled ->
                _uiState.update { it.copy(skipOutroEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            preferencesManager.skipSelfPromoEnabled.collect { enabled ->
                _uiState.update { it.copy(skipSelfPromoEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            preferencesManager.skipMusicOffTopicEnabled.collect { enabled ->
                _uiState.update { it.copy(skipMusicOffTopicEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            preferencesManager.downloadQuality.collect { quality ->
                _uiState.update { it.copy(downloadQuality = quality) }
            }
        }
        viewModelScope.launch {
            preferencesManager.videoPlaybackQuality.collect { quality ->
                _uiState.update { it.copy(videoPlaybackQuality = quality) }
            }
        }
        viewModelScope.launch {
            preferencesManager.contentLanguage.collect { languageRaw ->
                val language = normalizeLanguageOrDefault(languageRaw)
                _uiState.update {
                    val preferred = if (it.preferredLanguages.isEmpty()) setOf(language) else it.preferredLanguages
                    it.copy(contentLanguage = language, preferredLanguages = preferred)
                }
            }
        }
        viewModelScope.launch {
            preferencesManager.contentCountry.collect { countryRaw ->
                val country = normalizeCountryOrDefault(countryRaw)
                _uiState.update { it.copy(contentCountry = country) }
            }
        }
        viewModelScope.launch {
            preferencesManager.preferredLanguages.collect { languagesRaw ->
                val normalized = RecommendationContentRules
                    .normalizeLanguages(languagesRaw.filterNot { it.equals("None", ignoreCase = true) })
                _uiState.update {
                    val fallback = setOf(it.contentLanguage)
                    it.copy(preferredLanguages = if (normalized.isEmpty()) fallback else normalized)
                }
            }
        }
        viewModelScope.launch {
            preferencesManager.maxCacheSizeMb.collect { maxCacheSize ->
                _uiState.update { it.copy(maxCacheSizeMb = maxCacheSize) }
            }
        }
    }

    fun refreshIdentityState() {
        viewModelScope.launch {
            val userId = authManager.getCurrentUserId()
            val identityMode = when (authManager.getIdentityMode()) {
                IdentityMode.GOOGLE -> "Google"
                IdentityMode.ANONYMOUS -> "Guest"
                IdentityMode.LOCAL -> "Local"
            }
            _uiState.update {
                it.copy(
                    currentUserId = userId,
                    identityModeLabel = identityMode,
                    cloudSyncAvailable = authManager.hasCloudUser()
                )
            }
        }
    }

    fun loginWithGoogle(activity: Activity) {
        viewModelScope.launch {
            val previousUserId = authManager.getCurrentUserId()
            val previousWasAnonymous = authManager.isAnonymousSession()
            _uiState.update { it.copy(isIdentityActionInProgress = true, statusMessage = null) }

            authManager.loginWithGoogle(activity)
                .onSuccess { newUserId ->
                    val migrationResult = if (previousWasAnonymous && previousUserId != newUserId) {
                        syncManager.mergeGuestDataToCurrentUser(
                            guestUserId = previousUserId,
                            targetUserId = newUserId,
                            deleteSource = true
                        )
                    } else {
                        Result.success(Unit)
                    }

                    migrationResult.fold(
                        onSuccess = {
                            syncManager.mergeLocalAndRemote().fold(
                                onSuccess = {
                                    syncScheduler.schedulePeriodicSync()
                                    syncScheduler.enqueueImmediateSync()
                                    refreshIdentityState()
                                    _uiState.update {
                                        it.copy(
                                            isIdentityActionInProgress = false,
                                            statusMessage = "Signed in with Google and sync completed."
                                        )
                                    }
                                },
                                onFailure = { error ->
                                    refreshIdentityState()
                                    _uiState.update {
                                        it.copy(
                                            isIdentityActionInProgress = false,
                                            statusMessage = error.message ?: "Signed in, but sync failed"
                                        )
                                    }
                                }
                            )
                        },
                        onFailure = { error ->
                            refreshIdentityState()
                            _uiState.update {
                                it.copy(
                                    isIdentityActionInProgress = false,
                                    statusMessage = error.message ?: "Signed in, but guest data migration failed"
                                )
                            }
                        }
                    )
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isIdentityActionInProgress = false,
                            statusMessage = error.message ?: "Google sign-in failed"
                        )
                    }
                }
        }
    }

    fun loginAnonymously() {
        viewModelScope.launch {
            _uiState.update { it.copy(isIdentityActionInProgress = true, statusMessage = null) }
            authManager.loginAnonymously()
                .onSuccess {
                    syncManager.mergeLocalAndRemote().fold(
                        onSuccess = {
                            syncScheduler.schedulePeriodicSync()
                            syncScheduler.enqueueImmediateSync()
                            refreshIdentityState()
                            _uiState.update {
                                it.copy(
                                    isIdentityActionInProgress = false,
                                    statusMessage = "Guest cloud sync enabled and data synchronized."
                                )
                            }
                        },
                        onFailure = { error ->
                            refreshIdentityState()
                            _uiState.update {
                                it.copy(
                                    isIdentityActionInProgress = false,
                                    statusMessage = error.message ?: "Guest login succeeded, but sync failed"
                                )
                            }
                        }
                    )
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isIdentityActionInProgress = false,
                            statusMessage = error.message ?: "Guest sign-in failed"
                        )
                    }
                }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isIdentityActionInProgress = true, statusMessage = null) }
            ensureCloudSessionForSync().fold(
                onSuccess = {
                    syncManager.mergeLocalAndRemote()
                        .onSuccess {
                            syncScheduler.enqueueImmediateSync()
                            _uiState.update {
                                it.copy(
                                    isIdentityActionInProgress = false,
                                    statusMessage = "Sync completed successfully."
                                )
                            }
                        }
                        .onFailure { error ->
                            _uiState.update {
                                it.copy(
                                    isIdentityActionInProgress = false,
                                    statusMessage = error.message ?: "Sync failed"
                                )
                            }
                        }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isIdentityActionInProgress = false,
                            statusMessage = error.message ?: "Cloud session unavailable"
                        )
                    }
                }
            )
        }
    }

    fun backupNow() {
        syncNow()
    }

    fun exportDataSnapshot() {
        viewModelScope.launch {
            _uiState.update { it.copy(isIdentityActionInProgress = true, statusMessage = null) }
            runCatching {
                preferencesManager.exportSyncPreferences()
            }.onSuccess { exported ->
                _uiState.update {
                    it.copy(
                        isIdentityActionInProgress = false,
                        statusMessage = "Export snapshot prepared (${exported.size} entries)."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isIdentityActionInProgress = false,
                        statusMessage = error.message ?: "Failed to export data snapshot"
                    )
                }
            }
        }
    }

    fun deleteMyData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isIdentityActionInProgress = true, statusMessage = null) }
            syncManager.deleteMyData()
                .onSuccess {
                    refreshIdentityState()
                    _uiState.update {
                        it.copy(
                            isIdentityActionInProgress = false,
                            statusMessage = "Your local and cloud data has been deleted."
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isIdentityActionInProgress = false,
                            statusMessage = error.message ?: "Data deletion failed"
                        )
                    }
                }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
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

    fun setVideoPlaybackQuality(quality: Int) {
        viewModelScope.launch {
            preferencesManager.setVideoPlaybackQuality(quality)
        }
    }

    fun setContentLanguage(language: String) {
        viewModelScope.launch {
            val normalized = normalizeLanguageOrDefault(language)
            preferencesManager.setContentLanguage(normalized)

            val currentPreferred = RecommendationContentRules
                .normalizeLanguages(preferencesManager.preferredLanguages.first())
                .toMutableSet()
            if (!currentPreferred.contains(normalized)) {
                currentPreferred.add(normalized)
                preferencesManager.setPreferredLanguages(currentPreferred)
            }
        }
    }

    fun setContentCountry(country: String) {
        viewModelScope.launch {
            val normalized = normalizeCountryOrDefault(country)
            preferencesManager.setContentCountry(normalized)
        }
    }

    fun togglePreferredLanguage(language: String) {
        viewModelScope.launch {
            val normalized = normalizeLanguageOrDefault(language)
            val updated = RecommendationContentRules
                .normalizeLanguages(preferencesManager.preferredLanguages.first())
                .toMutableSet()

            if (updated.contains(normalized) && updated.size > 1) {
                updated.remove(normalized)
            } else {
                updated.add(normalized)
            }

            if (updated.isEmpty()) {
                updated.add(normalizeLanguageOrDefault(_uiState.value.contentLanguage))
            }

            preferencesManager.setPreferredLanguages(updated)

            val currentContentLanguage = normalizeLanguageOrDefault(_uiState.value.contentLanguage)
            if (!updated.contains(currentContentLanguage)) {
                preferencesManager.setContentLanguage(updated.first())
            }
        }
    }

    fun setMaxCacheSize(sizeMb: Int) {
        viewModelScope.launch {
            preferencesManager.setMaxCacheSizeMb(sizeMb)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(currentCacheSizeMb = 0) }
        }
    }

    private fun normalizeLanguageOrDefault(value: String?): String {
        return RecommendationContentRules.normalizeLanguage(value) ?: "English"
    }

    private fun normalizeCountryOrDefault(value: String?): String {
        return RecommendationContentRules.normalizeCountry(value) ?: "US"
    }

    private suspend fun ensureCloudSessionForSync(): Result<Unit> {
        if (authManager.hasCloudUser() && !authManager.getCurrentFirebaseUid().isNullOrBlank()) {
            return Result.success(Unit)
        }

        return authManager.loginAnonymously()
            .map { Unit }
            .onSuccess {
                refreshIdentityState()
                syncScheduler.schedulePeriodicSync()
            }
            .recoverCatching { error ->
                throw IllegalStateException(
                    error.message
                        ?: "Cloud sync is unavailable. Verify Firebase project configuration and network connectivity.",
                    error
                )
            }
    }
}
