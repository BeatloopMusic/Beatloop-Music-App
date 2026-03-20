package com.beatloop.music.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "beatloop_preferences")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore
    
    // Theme preferences
    val themeMode: Flow<ThemeMode> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            ThemeMode.fromValue(preferences[THEME_MODE] ?: ThemeMode.SYSTEM.value)
        }
    
    val dynamicColors: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[DYNAMIC_COLORS] ?: true }
    
    // Alias for dynamicColors
    val dynamicColorEnabled: Flow<Boolean> get() = dynamicColors
    
    val amoledBlack: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[AMOLED_BLACK] ?: false }
    
    // Alias for amoledBlack
    val pureBlackEnabled: Flow<Boolean> get() = amoledBlack
    
    // Audio preferences
    val audioQuality: Flow<AudioQuality> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            AudioQuality.fromValue(preferences[AUDIO_QUALITY] ?: AudioQuality.HIGH.value)
        }
    
    val skipSilence: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[SKIP_SILENCE] ?: false }
    
    // Alias for skipSilence
    val skipSilenceEnabled: Flow<Boolean> get() = skipSilence
    
    val audioNormalization: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[AUDIO_NORMALIZATION] ?: true }
    
    // Alias for audioNormalization
    val normalizeAudioEnabled: Flow<Boolean> get() = audioNormalization
    
    // Persistent queue preference
    val persistentQueueEnabled: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PERSISTENT_QUEUE] ?: true }
    
    // SponsorBlock preferences
    val sponsorBlockEnabled: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[SPONSORBLOCK_ENABLED] ?: true }
    
    val skipSponsor: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[SKIP_SPONSOR] ?: true }
    
    val skipSelfPromo: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[SKIP_SELFPROMO] ?: true }
    
    val skipIntro: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[SKIP_INTRO] ?: true }
    
    // Alias for skipIntro
    val skipIntroEnabled: Flow<Boolean> get() = skipIntro
    
    val skipOutro: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[SKIP_OUTRO] ?: true }
    
    // Alias for skipOutro
    val skipOutroEnabled: Flow<Boolean> get() = skipOutro
    
    // Alias for skipSelfPromo
    val skipSelfPromoEnabled: Flow<Boolean> get() = skipSelfPromo
    
    // Music off topic preference
    val skipMusicOffTopicEnabled: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[SKIP_MUSIC_OFFTOPIC] ?: true }
    
    // Content preferences
    val contentLanguage: Flow<String> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[CONTENT_LANGUAGE] ?: "en" }
    
    val contentCountry: Flow<String> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[CONTENT_COUNTRY] ?: "US" }

    // Onboarding and recommendation preference profile
    val onboardingCompleted: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[ONBOARDING_COMPLETED] ?: false }

    val preferredLanguages: Flow<Set<String>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PREFERRED_LANGUAGES] ?: emptySet() }

    val preferredSingers: Flow<Set<String>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PREFERRED_SINGERS] ?: emptySet() }

    val preferredLyricists: Flow<Set<String>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PREFERRED_LYRICISTS] ?: emptySet() }

    val preferredMusicDirectors: Flow<Set<String>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PREFERRED_MUSIC_DIRECTORS] ?: emptySet() }
    
    // Cache preferences
    val maxCacheSize: Flow<Long> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[MAX_CACHE_SIZE] ?: 512L }
    
    // Alias for maxCacheSize (as Int for MB)
    val maxCacheSizeMb: Flow<Int> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { (it[MAX_CACHE_SIZE] ?: 512L).toInt() }
    
    // Download quality preference
    val downloadQuality: Flow<AudioQuality> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            AudioQuality.fromValue(preferences[DOWNLOAD_QUALITY] ?: AudioQuality.VERY_HIGH.value)
        }
    
    // Update functions
    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.value }
    }
    
    suspend fun setDynamicColors(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLORS] = enabled }
    }
    
    suspend fun setAmoledBlack(enabled: Boolean) {
        dataStore.edit { it[AMOLED_BLACK] = enabled }
    }
    
    // Alias for setAmoledBlack
    suspend fun setPureBlackEnabled(enabled: Boolean) = setAmoledBlack(enabled)
    
    // Alias for setDynamicColors
    suspend fun setDynamicColorEnabled(enabled: Boolean) = setDynamicColors(enabled)
    
    suspend fun setAudioQuality(quality: AudioQuality) {
        dataStore.edit { it[AUDIO_QUALITY] = quality.value }
    }
    
    suspend fun setSkipSilence(enabled: Boolean) {
        dataStore.edit { it[SKIP_SILENCE] = enabled }
    }
    
    // Alias for setSkipSilence
    suspend fun setSkipSilenceEnabled(enabled: Boolean) = setSkipSilence(enabled)
    
    suspend fun setAudioNormalization(enabled: Boolean) {
        dataStore.edit { it[AUDIO_NORMALIZATION] = enabled }
    }
    
    // Alias for setAudioNormalization
    suspend fun setNormalizeAudioEnabled(enabled: Boolean) = setAudioNormalization(enabled)
    
    suspend fun setPersistentQueueEnabled(enabled: Boolean) {
        dataStore.edit { it[PERSISTENT_QUEUE] = enabled }
    }
    
    suspend fun setSponsorBlockEnabled(enabled: Boolean) {
        dataStore.edit { it[SPONSORBLOCK_ENABLED] = enabled }
    }
    
    suspend fun setSkipSponsor(enabled: Boolean) {
        dataStore.edit { it[SKIP_SPONSOR] = enabled }
    }
    
    suspend fun setSkipSelfPromo(enabled: Boolean) {
        dataStore.edit { it[SKIP_SELFPROMO] = enabled }
    }
    
    suspend fun setSkipIntro(enabled: Boolean) {
        dataStore.edit { it[SKIP_INTRO] = enabled }
    }
    
    // Alias for setSkipIntro
    suspend fun setSkipIntroEnabled(enabled: Boolean) = setSkipIntro(enabled)
    
    suspend fun setSkipOutro(enabled: Boolean) {
        dataStore.edit { it[SKIP_OUTRO] = enabled }
    }
    
    // Alias for setSkipOutro
    suspend fun setSkipOutroEnabled(enabled: Boolean) = setSkipOutro(enabled)
    
    // Alias for setSkipSelfPromo
    suspend fun setSkipSelfPromoEnabled(enabled: Boolean) = setSkipSelfPromo(enabled)
    
    suspend fun setSkipMusicOffTopicEnabled(enabled: Boolean) {
        dataStore.edit { it[SKIP_MUSIC_OFFTOPIC] = enabled }
    }
    
    suspend fun setContentLanguage(language: String) {
        dataStore.edit { it[CONTENT_LANGUAGE] = language }
    }
    
    suspend fun setContentCountry(country: String) {
        dataStore.edit { it[CONTENT_COUNTRY] = country }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setPreferredLanguages(languages: Set<String>) {
        dataStore.edit { it[PREFERRED_LANGUAGES] = languages }
    }

    suspend fun setPreferredSingers(singers: Set<String>) {
        dataStore.edit { it[PREFERRED_SINGERS] = singers }
    }

    suspend fun setPreferredLyricists(lyricists: Set<String>) {
        dataStore.edit { it[PREFERRED_LYRICISTS] = lyricists }
    }

    suspend fun setPreferredMusicDirectors(directors: Set<String>) {
        dataStore.edit { it[PREFERRED_MUSIC_DIRECTORS] = directors }
    }

    suspend fun saveOnboardingPreferences(
        languages: Set<String>,
        singers: Set<String>,
        lyricists: Set<String>,
        musicDirectors: Set<String>
    ) {
        dataStore.edit {
            it[PREFERRED_LANGUAGES] = languages
            it[PREFERRED_SINGERS] = singers
            it[PREFERRED_LYRICISTS] = lyricists
            it[PREFERRED_MUSIC_DIRECTORS] = musicDirectors
            it[ONBOARDING_COMPLETED] = true
            // Default to highest quality for first-time personalization experience.
            it[AUDIO_QUALITY] = AudioQuality.BEST.value
            it[DOWNLOAD_QUALITY] = AudioQuality.BEST.value
        }
    }
    
    suspend fun setMaxCacheSize(size: Long) {
        dataStore.edit { it[MAX_CACHE_SIZE] = size }
    }
    
    // Alias for setMaxCacheSize (takes Int for MB)
    suspend fun setMaxCacheSizeMb(sizeMb: Int) {
        dataStore.edit { it[MAX_CACHE_SIZE] = sizeMb.toLong() }
    }
    
    suspend fun setDownloadQuality(quality: AudioQuality) {
        dataStore.edit { it[DOWNLOAD_QUALITY] = quality.value }
    }
    
    companion object {
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        private val AMOLED_BLACK = booleanPreferencesKey("amoled_black")
        private val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        private val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        private val AUDIO_NORMALIZATION = booleanPreferencesKey("audio_normalization")
        private val PERSISTENT_QUEUE = booleanPreferencesKey("persistent_queue")
        private val SPONSORBLOCK_ENABLED = booleanPreferencesKey("sponsorblock_enabled")
        private val SKIP_SPONSOR = booleanPreferencesKey("skip_sponsor")
        private val SKIP_SELFPROMO = booleanPreferencesKey("skip_selfpromo")
        private val SKIP_INTRO = booleanPreferencesKey("skip_intro")
        private val SKIP_OUTRO = booleanPreferencesKey("skip_outro")
        private val SKIP_MUSIC_OFFTOPIC = booleanPreferencesKey("skip_music_offtopic")
        private val CONTENT_LANGUAGE = stringPreferencesKey("content_language")
        private val CONTENT_COUNTRY = stringPreferencesKey("content_country")
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val PREFERRED_LANGUAGES = stringSetPreferencesKey("preferred_languages")
        private val PREFERRED_SINGERS = stringSetPreferencesKey("preferred_singers")
        private val PREFERRED_LYRICISTS = stringSetPreferencesKey("preferred_lyricists")
        private val PREFERRED_MUSIC_DIRECTORS = stringSetPreferencesKey("preferred_music_directors")
        private val MAX_CACHE_SIZE = longPreferencesKey("max_cache_size")
        private val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
    }
}

enum class ThemeMode(val value: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");
    
    companion object {
        fun fromValue(value: String): ThemeMode {
            return entries.find { it.value == value } ?: SYSTEM
        }
    }
}

enum class AudioQuality(val value: String, val displayName: String, val bitrate: Int) {
    LOW("low", "Low (128kbps)", 128),
    MEDIUM("medium", "Medium (192kbps)", 192),
    HIGH("high", "High (256kbps)", 256),
    VERY_HIGH("very_high", "Very High (320kbps)", 320),
    BEST("best", "Best (320kbps)", 320);
    
    companion object {
        fun fromValue(value: String): AudioQuality {
            return entries.find { it.value == value } ?: HIGH
        }
    }
}
