package com.beatloop.music.data.repository

import android.util.Log
import com.beatloop.music.data.api.InnerTubeApi
import com.beatloop.music.data.api.ReturnYouTubeDislikeApi
import com.beatloop.music.data.database.ArtistPlayStat
import com.beatloop.music.data.database.DownloadedSongDao
import com.beatloop.music.data.database.DownloadedSong
import com.beatloop.music.data.database.DeletedPlaylistSyncEntity
import com.beatloop.music.data.database.InteractionSignalTypes
import com.beatloop.music.data.database.ListeningSessionEvent
import com.beatloop.music.data.database.SongDao
import com.beatloop.music.data.database.SearchHistoryDao
import com.beatloop.music.data.database.SearchHistory
import com.beatloop.music.data.database.SyncDao
import com.beatloop.music.data.model.*
import com.beatloop.music.data.preferences.PreferencesManager
import com.beatloop.music.domain.recommendation.RecommendationCandidate
import com.beatloop.music.domain.recommendation.RecommendationContentRules
import com.beatloop.music.domain.recommendation.RecommendationSource
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo

@Singleton
class MusicRepository @Inject constructor(
    private val innerTubeApi: InnerTubeApi,
    private val returnYouTubeDislikeApi: ReturnYouTubeDislikeApi,
    private val songDao: SongDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val downloadedSongDao: DownloadedSongDao,
    private val syncDao: SyncDao,
    private val preferencesManager: PreferencesManager,
    private val recommendationRepository: RecommendationRepository
) {
    private data class OnboardingSeedCache(
        val key: String,
        val songs: List<SongItem>,
        val timestampMs: Long
    )

    private data class GenreSectionsCache(
        val key: String,
        val sections: List<GenreRecommendationSection>,
        val timestampMs: Long
    )

    private var onboardingSeedCache: OnboardingSeedCache? = null
    private var genreSectionsCache: GenreSectionsCache? = null

    private val pipedApiInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://pipedapi.leptons.xyz",
        "https://pipedapi.nosebs.ru",
        "https://piped-api.codespace.cz",
        "https://api.piped.private.coffee",
        "https://pipedapi.orangenet.cc",
        "https://pipedapi.syncpundit.io"
    )

    private val invidiousApiInstances = listOf(
        "https://inv.nadeko.net",
        "https://vid.puffyan.us",
        "https://invidious.private.coffee",
        "https://invidious.privacyredirect.com"
    )

    private val pipedStreamEndpointTemplates = listOf(
        "/streams/%s",
        "/api/v1/streams/%s",
        "/api/streams/%s"
    )

    @Volatile
    private var pipedInstancesCache: Pair<List<String>, Long>? = null

    private enum class SignatureOperationType {
        REVERSE,
        SLICE,
        SWAP
    }

    private data class SignatureOperation(
        val type: SignatureOperationType,
        val argument: Int = 0
    )

    private data class SignatureDecipherPlan(
        val playerScriptUrl: String,
        val operations: List<SignatureOperation>
    )

    private enum class StreamMode {
        AUDIO,
        VIDEO
    }

    @Volatile
    private var signatureDecipherPlanCache: SignatureDecipherPlan? = null

    private val extractorHttpClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val languageSearchHints: Map<String, List<String>> = mapOf(
        "hindi" to listOf("hindi songs", "bollywood hits"),
        "tamil" to listOf("tamil songs", "kollywood hits"),
        "telugu" to listOf("telugu songs", "tollywood hits"),
        "malayalam" to listOf("malayalam songs"),
        "kannada" to listOf("kannada songs"),
        "punjabi" to listOf("punjabi songs"),
        "english" to listOf("english pop songs"),
        "spanish" to listOf("spanish songs"),
        "arabic" to listOf("arabic songs"),
        "japanese" to listOf("japanese songs", "j-pop songs"),
        "korean" to listOf("korean songs", "k-pop songs")
    )

    companion object {
        private const val TAG = "MusicRepository"
        private const val PIPED_INSTANCE_LIST_URL = "https://raw.githubusercontent.com/TeamPiped/documentation/main/content/docs/public-instances/index.md"
        private const val PIPED_INSTANCE_CACHE_TTL_MS = 30 * 60 * 1000L
        private const val STRATEGY_TIMEOUT_MS = 10_000L
        private const val STRATEGY_TIMEOUT_SLOW_MS = 15_000L
        private const val HOME_RECOMMENDATION_MIN_SIZE = 12
        private const val HOME_YOUTUBE_FALLBACK_MAX_SEEDS = 4
        private const val PLAYLIST_SAVE_MAX_PAGES = 20
        private val newPipeInitLock = Any()

        @Volatile
        private var newPipeInitialized = false

        // Generate a consistent visitor ID for the session
        private val visitorData = generateVisitorData()
        
        private fun generateVisitorData(): String {
            // Generate a visitor data string similar to what YouTube uses
            val timestamp = System.currentTimeMillis() / 1000
            return "CgtBWWR3${UUID.randomUUID().toString().take(8)}${timestamp % 1000000}"
        }
    }
    
    // WEB_REMIX context for browse/search (YouTube Music web client)
    private fun createWebContext(): JsonObject {
        return JsonObject().apply {
            add("client", JsonObject().apply {
                addProperty("clientName", "WEB_REMIX")
                addProperty("clientVersion", "1.20231204.01.00")
                addProperty("hl", "en")
                addProperty("gl", "US")
                addProperty("visitorData", visitorData)
                addProperty("userAgent", InnerTubeApi.WEB_USER_AGENT)
                addProperty("originalUrl", "https://music.youtube.com/")
                addProperty("platform", "DESKTOP")
            })
        }
    }
    
    // ANDROID_MUSIC client context for player requests (streaming)
    private fun createAndroidContext(): JsonObject {
        return JsonObject().apply {
            add("client", JsonObject().apply {
                addProperty("clientName", "ANDROID_MUSIC")
                addProperty("clientVersion", "5.01")
                addProperty("androidSdkVersion", 30)
                addProperty("hl", "en")
                addProperty("gl", "US")
                addProperty("visitorData", visitorData)
            })
        }
    }

    // ANDROID_VR client context inspired by OuterTune stream fallback matrix.
    private fun createAndroidVrContext(): JsonObject {
        return JsonObject().apply {
            add("client", JsonObject().apply {
                addProperty("clientName", "ANDROID_VR")
                addProperty("clientVersion", "1.61.48")
                addProperty("androidSdkVersion", 32)
                addProperty("hl", "en")
                addProperty("gl", "US")
                addProperty("visitorData", visitorData)
            })
        }
    }

    // Generic ANDROID client context used as a secondary stream source.
    private fun createAndroidClientContext(): JsonObject {
        return JsonObject().apply {
            add("client", JsonObject().apply {
                addProperty("clientName", "ANDROID")
                addProperty("clientVersion", "20.10.38")
                addProperty("androidSdkVersion", 30)
                addProperty("hl", "en")
                addProperty("gl", "US")
                addProperty("visitorData", visitorData)
            })
        }
    }

    // WEB_CREATOR context (OuterTune uses a broader web-client matrix).
    private fun createWebCreatorContext(): JsonObject {
        return JsonObject().apply {
            add("client", JsonObject().apply {
                addProperty("clientName", "WEB_CREATOR")
                addProperty("clientVersion", "1.20250312.03.01")
                addProperty("hl", "en")
                addProperty("gl", "US")
                addProperty("visitorData", visitorData)
                addProperty("userAgent", InnerTubeApi.WEB_USER_AGENT)
                addProperty("platform", "DESKTOP")
            })
        }
    }
    
    // iOS client context - often works better for streaming
    private fun createiOSContext(): JsonObject {
        return JsonObject().apply {
            add("client", JsonObject().apply {
                addProperty("clientName", "IOS")
                addProperty("clientVersion", "19.29.1")
                addProperty("deviceMake", "Apple")
                addProperty("deviceModel", "iPhone16,2")
                addProperty("hl", "en")
                addProperty("gl", "US")
                addProperty("osName", "iPhone")
                addProperty("osVersion", "17.5.1.21F90")
                addProperty("visitorData", visitorData)
            })
        }
    }
    
    // TVHTML5 client context - used as fallback for age-restricted content
    private fun createTVContext(): JsonObject {
        return JsonObject().apply {
            add("client", JsonObject().apply {
                addProperty("clientName", "TVHTML5_SIMPLY_EMBEDDED_PLAYER")
                addProperty("clientVersion", "2.0")
                addProperty("hl", "en")
                addProperty("gl", "US")
                addProperty("visitorData", visitorData)
            })
        }
    }
    
    // Legacy method for backward compatibility
    private fun createContext(): JsonObject = createWebContext()
    
    suspend fun search(query: String, filter: SearchFilter = SearchFilter.All): Result<SearchResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching for: $query")
            val body = JsonObject().apply {
                addProperty("query", query)
                add("context", createWebContext())
            }
            
            val response = innerTubeApi.search(body)
            Log.d(TAG, "Search response received, parsing...")
            val result = parseSearchResult(response, filter)
            Log.d(TAG, "Search parsed: ${result.songs.size} songs, ${result.artists.size} artists, ${result.albums.size} albums")
            
            // Save to search history
            searchHistoryDao.upsertQuery(query)
            runCatching { recommendationRepository.recordSearchQuery(query) }
                .onFailure { error -> Log.w(TAG, "Failed to track search signal", error) }
            
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            Result.failure(e)
        }
    }
    
    suspend fun getSearchSuggestions(query: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting search suggestions for: $query")
            val body = JsonObject().apply {
                addProperty("input", query)
                add("context", createWebContext())
            }
            
            val response = innerTubeApi.getSearchSuggestions(body)
            val suggestions = parseSearchSuggestions(response)
            Log.d(TAG, "Got ${suggestions.size} suggestions")
            Result.success(suggestions)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get search suggestions", e)
            Result.failure(e)
        }
    }
    
    suspend fun getHome(refreshNonce: Long = System.currentTimeMillis()): Result<HomeContent> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Loading home content...")
            val body = JsonObject().apply {
                addProperty("browseId", "FEmusic_home")
                add("context", createWebContext())
            }
            
            val response = innerTubeApi.browse(body)
            Log.d(TAG, "Home response received, parsing...")
            val baseHomeContent = parseHomeContent(response)
            val homeContent = personalizeHomeContent(baseHomeContent, refreshNonce)
            Log.d(TAG, "Home parsed: ${homeContent.quickPicks.size} quick picks, ${homeContent.trendingSongs.size} trending, ${homeContent.newReleases.size} new releases")
            Result.success(homeContent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load home content", e)
            Result.failure(e)
        }
    }

    suspend fun getVideoVotes(videoId: String): Result<VideoVotes> = withContext(Dispatchers.IO) {
        try {
            if (videoId.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("videoId is blank"))
            }

            val response = returnYouTubeDislikeApi.getVotes(videoId)
            val votes = VideoVotes(
                videoId = response.id.ifBlank { videoId },
                likes = response.likes ?: 0L,
                dislikes = response.dislikes ?: 0L,
                viewCount = response.viewCount ?: 0L,
                rating = response.rating
            )
            Result.success(votes)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load ReturnYouTubeDislike votes for $videoId: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun personalizeHomeContent(base: HomeContent, refreshNonce: Long): HomeContent {
        return try {
            val seedSongs = songDao.getPersonalizationSeedSongs(limit = 150)
            val recentSongs = songDao.getRecentlyPlayedSongItems(limit = 30)
            val topArtistStats = songDao.getTopArtistPlayStats(limit = 30)
            val preferenceProfile = getPreferenceProfile()
            val activeLanguages = preferenceProfile.activeLanguages
            val onboardingSeedSongs = getOnboardingSeedSongs(preferenceProfile)

            val filteredQuickPicks = applyLanguageFilter(filterEligibleSongs(base.quickPicks), activeLanguages)
            val filteredTrendingSongs = applyLanguageFilter(filterEligibleSongs(base.trendingSongs), activeLanguages)
            val filteredRecentSongs = applyLanguageFilter(
                filterEligibleSongs(recentSongs),
                activeLanguages,
                allowUnknownLanguage = true
            )
            val filteredSeedSongs = applyLanguageFilter(
                filterEligibleSongs(seedSongs.map { it.toSongItem() }),
                activeLanguages,
                allowUnknownLanguage = true
            )
            val filteredOnboardingSongs = applyLanguageFilter(filterEligibleSongs(onboardingSeedSongs), activeLanguages)

            val topArtists = buildTopArtists(seedSongs, topArtistStats)
            val weightedArtists: List<String> = (topArtists + preferenceProfile.singers)
                .distinct()
                .take(8)
            val topArtistWeight = weightedArtists
                .mapIndexed { index, artist -> artist to (40 - index * 6).coerceAtLeast(8) }
                .toMap()

            val recommendationCandidates = (
                filteredQuickPicks.map {
                    RecommendationCandidate(
                        song = it,
                        source = RecommendationSource.QUICK_PICK,
                        sourceBoost = 12.0
                    )
                } +
                    filteredTrendingSongs.map {
                        RecommendationCandidate(
                            song = it,
                            source = RecommendationSource.TRENDING,
                            sourceBoost = 7.0
                        )
                    } +
                    filteredRecentSongs.map {
                        RecommendationCandidate(
                            song = it,
                            source = RecommendationSource.RECENT,
                            sourceBoost = 3.0
                        )
                    } +
                    filteredSeedSongs.map {
                        RecommendationCandidate(
                            song = it,
                            source = RecommendationSource.PERSONALIZATION_SEED,
                            sourceBoost = 8.0
                        )
                    } +
                    filteredOnboardingSongs.map {
                        RecommendationCandidate(
                            song = it,
                            source = RecommendationSource.ONBOARDING,
                            sourceBoost = 10.0
                        )
                    }
                )
                .distinctBy { it.song.id }

            val personalized = recommendationRepository
                .rankHomeCandidates(
                    candidates = recommendationCandidates,
                    topArtistWeights = topArtistWeight,
                    refreshNonce = refreshNonce,
                    limit = 30
                )

            val recommendationSeedSongs = (filteredRecentSongs + filteredSeedSongs + filteredQuickPicks + filteredOnboardingSongs)
                .filter { song -> song.id.isNotBlank() }
                .distinctBy { song -> song.id }

            val youtubeFallbackRecommendations = if (personalized.size < HOME_RECOMMENDATION_MIN_SIZE) {
                fetchYouTubeFallbackRecommendations(
                    seedSongs = recommendationSeedSongs,
                    activeLanguages = activeLanguages,
                    excludedSongIds = recommendationCandidates.map { candidate -> candidate.song.id }.toSet()
                )
            } else {
                emptyList()
            }

            val personalizedWithFallback = (personalized + youtubeFallbackRecommendations)
                .distinctBy { song -> song.id }
                .take(30)

            val motivationMessage = when {
                filteredOnboardingSongs.isNotEmpty() && preferenceProfile.singers.isNotEmpty() ->
                    "Recommendations tuned for ${preferenceProfile.singers.first()} and your selected vibe"
                personalizedWithFallback.isNotEmpty() && youtubeFallbackRecommendations.isNotEmpty() ->
                    "Blended with YouTube recommendations for fresher picks"
                personalizedWithFallback.isNotEmpty() && weightedArtists.isNotEmpty() ->
                    "Fresh picks based on your ${weightedArtists.first()} listening taste"
                filteredRecentSongs.isNotEmpty() -> "Welcome back. Your recent vibe is ready."
                activeLanguages.isNotEmpty() ->
                    "Handpicked recommendations for ${activeLanguages.take(2).joinToString(", ")}" 
                else -> "Start listening to unlock a personalized feed."
            }

            val personalizedPool = personalizedWithFallback.ifEmpty {
                (youtubeFallbackRecommendations + filteredOnboardingSongs + filteredTrendingSongs)
                    .distinctBy { song -> song.id }
                    .take(30)
            }

            val diversifiedPool = diversifyRecommendations(
                candidates = personalizedPool,
                recentlyPlayedIds = filteredRecentSongs.map { it.id }.toSet(),
                refreshNonce = refreshNonce
            )

            val fallbackQuickPicks = (youtubeFallbackRecommendations + filteredOnboardingSongs + filteredQuickPicks)
                .distinctBy { it.id }
                .take(15)

            val finalQuickPicks = diversifiedPool.take(15)
                .ifEmpty { fallbackQuickPicks.ifEmpty { filteredQuickPicks } }

            val finalQuickPickIds = finalQuickPicks.map { it.id }.toSet()

            val finalPersonalized = diversifiedPool
                .filterNot { finalQuickPickIds.contains(it.id) }
                .ifEmpty {
                    personalizedPool.filterNot { finalQuickPickIds.contains(it.id) }
                }
                .ifEmpty { diversifiedPool }

            runCatching {
                recommendationRepository.recordRecommendationImpressions(
                    songs = (finalQuickPicks + finalPersonalized).distinctBy { it.id },
                    surface = "home"
                )
            }

            val genreSections = buildGenreSections(preferenceProfile)

            base.copy(
                motivationMessage = motivationMessage,
                quickPicks = finalQuickPicks,
                personalizedRecommendations = finalPersonalized,
                recentlyPlayed = filteredRecentSongs.ifEmpty {
                    applyLanguageFilter(filterEligibleSongs(base.recentlyPlayed), activeLanguages, allowUnknownLanguage = true)
                },
                trendingSongs = filteredTrendingSongs,
                topArtists = weightedArtists.take(5).ifEmpty { preferenceProfile.singers.take(5) },
                genreSections = genreSections
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to personalize home content: ${e.message}")
            base
        }
    }

    private suspend fun getPreferenceProfile(): PreferenceProfile {
        val onboardingCompleted = preferencesManager.onboardingCompleted.first()
        val contentLanguage = RecommendationContentRules.normalizeLanguage(preferencesManager.contentLanguage.first())
        val preferredLanguages = RecommendationContentRules.normalizeLanguages(
            preferencesManager.preferredLanguages.first().filterNot { it.equals("None", ignoreCase = true) }
        )
        val activeLanguages = if (preferredLanguages.isNotEmpty()) {
            preferredLanguages
        } else {
            contentLanguage?.let { setOf(it) }.orEmpty()
        }

        if (!onboardingCompleted) {
            return PreferenceProfile(
                primaryLanguage = contentLanguage,
                activeLanguages = activeLanguages
            )
        }

        return PreferenceProfile(
            primaryLanguage = contentLanguage,
            activeLanguages = activeLanguages,
            singers = preferencesManager.preferredSingers.first().filterNot { it.equals("None", ignoreCase = true) }.toSet(),
            lyricists = preferencesManager.preferredLyricists.first().filterNot { it.equals("None", ignoreCase = true) }.toSet(),
            musicDirectors = preferencesManager.preferredMusicDirectors.first().filterNot { it.equals("None", ignoreCase = true) }.toSet()
        )
    }

    private data class PreferenceProfile(
        val primaryLanguage: String? = null,
        val activeLanguages: Set<String> = emptySet(),
        val singers: Set<String> = emptySet(),
        val lyricists: Set<String> = emptySet(),
        val musicDirectors: Set<String> = emptySet()
    )

    private fun PreferenceProfile.hasSelections(): Boolean {
        return activeLanguages.isNotEmpty() ||
            singers.isNotEmpty() ||
            lyricists.isNotEmpty() ||
            musicDirectors.isNotEmpty()
    }

    private suspend fun getOnboardingSeedSongs(preferenceProfile: PreferenceProfile): List<SongItem> {
        if (!preferenceProfile.hasSelections()) return emptyList()

        val key = buildString {
            append(preferenceProfile.activeLanguages.sorted().joinToString(","))
            append("|")
            append(preferenceProfile.singers.sorted().joinToString(","))
            append("|")
            append(preferenceProfile.lyricists.sorted().joinToString(","))
            append("|")
            append(preferenceProfile.musicDirectors.sorted().joinToString(","))
        }

        val now = System.currentTimeMillis()
        onboardingSeedCache?.let { cache ->
            if (cache.key == key && (now - cache.timestampMs) <= 20 * 60 * 1000) {
                return cache.songs
            }
        }

        val queries = buildOnboardingQueries(preferenceProfile)
        if (queries.isEmpty()) return emptyList()

        val songs = coroutineScope {
            queries
                .map { query ->
                    async {
                        fetchSeedSongsForQuery(
                            query = query,
                            preferredLanguages = preferenceProfile.activeLanguages
                        )
                    }
                }
                .awaitAll()
                .flatten()
        }
            .distinctBy { it.id }
            .take(80)

        onboardingSeedCache = OnboardingSeedCache(key = key, songs = songs, timestampMs = now)
        return songs
    }

    private fun buildOnboardingQueries(preferenceProfile: PreferenceProfile): List<String> {
        val singerQueries = preferenceProfile.singers
            .take(5)
            .flatMap { singer -> listOf("$singer hits", "$singer songs") }

        val languageQueries = preferenceProfile.activeLanguages
            .take(3)
            .flatMap { language ->
                languageSearchHints[language.lowercase()] ?: listOf("$language songs")
            }

        val lyricistQueries = preferenceProfile.lyricists
            .take(2)
            .map { lyricist -> "$lyricist songs" }

        val directorQueries = preferenceProfile.musicDirectors
            .take(2)
            .map { director -> "$director music" }

        return (singerQueries + languageQueries + lyricistQueries + directorQueries)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)
    }

    private suspend fun fetchSeedSongsForQuery(
        query: String,
        preferredLanguages: Set<String> = emptySet(),
        maxItems: Int = 12
    ): List<SongItem> {
        return try {
            val body = JsonObject().apply {
                addProperty("query", query)
                add("context", createWebContext())
            }
            val response = innerTubeApi.search(body)
            applyLanguageFilter(
                songs = filterEligibleSongs(parseSearchResult(response, SearchFilter.Songs).songs),
                activeLanguages = preferredLanguages,
                allowUnknownLanguage = true
            ).take(maxItems)
        } catch (e: Exception) {
            Log.w(TAG, "Seed query failed ($query): ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchYouTubeFallbackRecommendations(
        seedSongs: List<SongItem>,
        activeLanguages: Set<String>,
        excludedSongIds: Set<String>,
        maxItems: Int = 30
    ): List<SongItem> {
        val seedIds = seedSongs
            .asSequence()
            .map { song -> song.id.trim() }
            .filter { id ->
                id.isNotBlank() &&
                    !id.startsWith("content://") &&
                    !id.startsWith("file://")
            }
            .distinct()
            .take(HOME_YOUTUBE_FALLBACK_MAX_SEEDS)
            .toList()

        if (seedIds.isEmpty()) return emptyList()

        val fallbackSongs = coroutineScope {
            seedIds
                .map { seedId ->
                    async { getRelatedSongs(seedId).getOrDefault(emptyList()) }
                }
                .awaitAll()
                .flatten()
        }

        return applyLanguageFilter(
            songs = filterEligibleSongs(fallbackSongs)
                .asSequence()
                .filterNot { song -> excludedSongIds.contains(song.id) }
                .distinctBy { song -> song.id }
                .toList(),
            activeLanguages = activeLanguages,
            allowUnknownLanguage = true
        ).take(maxItems)
    }

    private suspend fun buildGenreSections(preferenceProfile: PreferenceProfile): List<GenreRecommendationSection> {
        val activeLanguages = preferenceProfile.activeLanguages
        val primaryLanguage = preferenceProfile.primaryLanguage ?: activeLanguages.firstOrNull()

        val cacheKey = buildString {
            append(primaryLanguage?.lowercase().orEmpty())
            append("|")
            append(activeLanguages.sorted().joinToString(","))
        }

        val now = System.currentTimeMillis()
        genreSectionsCache?.let { cache ->
            if (cache.key == cacheKey && (now - cache.timestampMs) <= 15 * 60 * 1000L) {
                return cache.sections
            }
        }

        val sectionQueries = listOf(
            "Trending" to "trending songs",
            "Melodies" to "melody songs",
            "Hits" to "hit songs",
            "Romantic Songs" to "romantic songs",
            "Love Songs" to "love songs",
            "DJ Songs" to "dj songs"
        )

        val sections = coroutineScope {
            sectionQueries.map { (title, queryTail) ->
                async {
                    val query = buildGenreQuery(primaryLanguage, queryTail)
                    val songs = fetchSeedSongsForQuery(
                        query = query,
                        preferredLanguages = activeLanguages,
                        maxItems = 16
                    )
                    if (songs.isEmpty()) {
                        null
                    } else {
                        GenreRecommendationSection(
                            title = title,
                            songs = songs.distinctBy { it.id }
                        )
                    }
                }
            }.awaitAll().filterNotNull()
        }

        genreSectionsCache = GenreSectionsCache(key = cacheKey, sections = sections, timestampMs = now)
        return sections
    }

    private fun buildGenreQuery(primaryLanguage: String?, queryTail: String): String {
        if (primaryLanguage.isNullOrBlank()) return queryTail
        return "${primaryLanguage.lowercase()} $queryTail"
    }

    private fun filterEligibleSongs(songs: List<SongItem>): List<SongItem> {
        return songs
            .asSequence()
            .filter { song ->
                song.id.isNotBlank() && RecommendationContentRules.isRecommendationTrackAllowed(song)
            }
            .distinctBy { song -> song.id }
            .toList()
    }

    private fun applyLanguageFilter(
        songs: List<SongItem>,
        activeLanguages: Set<String>,
        allowUnknownLanguage: Boolean = false
    ): List<SongItem> {
        if (songs.isEmpty()) return emptyList()
        if (activeLanguages.isEmpty()) return songs.distinctBy { it.id }

        return songs
            .asSequence()
            .filter { song ->
                RecommendationContentRules.matchesAllowedLanguages(
                    song = song,
                    allowedLanguages = activeLanguages,
                    allowUnknownLanguage = allowUnknownLanguage
                )
            }
            .distinctBy { song -> song.id }
            .toList()
    }

    private fun createSongItemIfEligible(
        id: String,
        title: String,
        artistsText: String = "",
        artistId: String? = null,
        thumbnailUrl: String? = null,
        albumId: String? = null,
        durationMs: Long? = null,
        localPath: String? = null
    ): SongItem? {
        val song = SongItem(
            id = id,
            title = title,
            artistsText = artistsText,
            artistId = artistId,
            thumbnailUrl = thumbnailUrl,
            albumId = albumId,
            duration = durationMs?.takeIf { it > 0L },
            localPath = localPath
        )

        return if (RecommendationContentRules.isTrackAllowed(song)) song else null
    }

    private fun parseDurationMs(rawText: String?): Long? {
        val text = rawText?.lowercase()?.trim().orEmpty()
        if (text.isBlank()) return null

        val hmsToken = Regex("(?<!\\d)(?:\\d{1,2}:)?\\d{1,2}:\\d{2}(?!\\d)")
            .find(text)
            ?.value

        if (!hmsToken.isNullOrBlank()) {
            val parts = hmsToken.split(":").mapNotNull { token -> token.toLongOrNull() }
            val seconds = when (parts.size) {
                2 -> parts[0] * 60L + parts[1]
                3 -> parts[0] * 3600L + parts[1] * 60L + parts[2]
                else -> null
            }
            if (seconds != null) return seconds * 1000L
        }

        val minuteValue = Regex("(?<!\\d)(\\d{1,3})\\s*(min|mins|minute|minutes)\\b")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()

        if (minuteValue != null) {
            return minuteValue * 60L * 1000L
        }

        val hourValue = Regex("(?<!\\d)(\\d{1,2})\\s*(hr|hrs|hour|hours)\\b")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()

        if (hourValue != null) {
            return hourValue * 60L * 60L * 1000L
        }

        return null
    }

    private fun parseDurationFromFixedColumns(fixedColumns: JsonArray?): Long? {
        val durationText = fixedColumns
            ?.firstOrNull()?.asJsonObject
            ?.getAsJsonObject("musicResponsiveListItemFixedColumnRenderer")
            ?.getAsJsonObject("text")
            ?.getAsJsonArray("runs")
            ?.joinToString(separator = "") { run ->
                run.asJsonObject?.get("text")?.asString.orEmpty()
            }

        return parseDurationMs(durationText)
    }

    private fun buildTopArtists(seedSongs: List<Song>, stats: List<ArtistPlayStat>): List<String> {
        val fromStats = stats
            .flatMap { stat ->
                splitArtists(stat.artistName).map { artist -> artist to stat.totalPlays }
            }

        val fromLikes = seedSongs
            .filter { it.liked }
            .flatMap { song ->
                splitArtists(song.artistsText).map { artist -> artist to 2L }
            }

        return (fromStats + fromLikes)
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, weights) -> weights.sum() }
            .toList()
            .sortedByDescending { (_, weight) -> weight }
            .map { (artist, _) -> artist }
            .take(5)
    }

    private fun splitArtists(artistsText: String): List<String> {
        return artistsText
            .split(",", "&", "feat.", "ft.", " and ", "/")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun diversifyRecommendations(
        candidates: List<SongItem>,
        recentlyPlayedIds: Set<String>,
        refreshNonce: Long
    ): List<SongItem> {
        if (candidates.isEmpty()) return emptyList()

        val freshnessPool = candidates.filterNot { recentlyPlayedIds.contains(it.id) }
            .ifEmpty { candidates }
            .distinctBy { it.id }

        if (freshnessPool.isEmpty()) return emptyList()

        val shuffled = freshnessPool.shuffled(Random(refreshNonce))
        if (shuffled.size <= 1) return shuffled

        val positiveNonce = if (refreshNonce < 0) -refreshNonce else refreshNonce
        val pivot = (positiveNonce % shuffled.size).toInt()
        return shuffled.drop(pivot) + shuffled.take(pivot)
    }

    private fun Song.toSongItem(): SongItem {
        return SongItem(
            id = id,
            title = title,
            artistsText = artistsText,
            artistId = artistId,
            thumbnailUrl = thumbnailUrl,
            albumId = albumId,
            duration = duration.takeIf { it > 0L },
            localPath = localPath
        )
    }
    
    suspend fun getArtist(artistId: String): Result<ArtistPage> = withContext(Dispatchers.IO) {
        try {
            val body = JsonObject().apply {
                addProperty("browseId", artistId)
                add("context", createContext())
            }
            
            val response = innerTubeApi.browse(body)
            val artistPage = parseArtistPage(response, artistId)
            Result.success(artistPage)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getAlbum(albumId: String): Result<AlbumPage> = withContext(Dispatchers.IO) {
        try {
            val body = JsonObject().apply {
                addProperty("browseId", albumId)
                add("context", createContext())
            }
            
            val response = innerTubeApi.browse(body)
            val albumPage = parseAlbumPage(response, albumId)
            Result.success(albumPage)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getPlaylist(playlistId: String): Result<PlaylistPage> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Loading playlist: $playlistId")
            val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
            
            val body = JsonObject().apply {
                addProperty("browseId", browseId)
                add("context", createWebContext())
            }
            
            val response = innerTubeApi.browse(body)
            val playlistPage = parsePlaylistPage(response, playlistId)
            Log.d(TAG, "Playlist loaded: ${playlistPage.songs.size} songs")
            Result.success(playlistPage)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load playlist", e)
            Result.failure(e)
        }
    }
    
    suspend fun getStreamUrl(videoId: String): Result<String> {
        return getStreamUrlInternal(videoId, mode = StreamMode.AUDIO, targetVideoHeight = 360)
    }

    suspend fun getVideoStreamUrl(videoId: String, targetVideoHeight: Int = 360): Result<String> {
        return getStreamUrlInternal(videoId, mode = StreamMode.VIDEO, targetVideoHeight = targetVideoHeight)
    }

    private suspend fun getStreamUrlInternal(
        videoId: String,
        mode: StreamMode,
        targetVideoHeight: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "===== STREAM RESOLUTION START for $videoId =====")

        suspend fun attempt(
            strategyName: String,
            timeoutMs: Long = STRATEGY_TIMEOUT_MS,
            block: suspend () -> String?
        ): String? {
            return try {
                Log.d(TAG, "Trying $strategyName...")
                val url = withTimeoutOrNull(timeoutMs) { block() }
                if (!url.isNullOrBlank() && isUsableStreamUrl(url)) {
                    Log.d(TAG, "===== SUCCESS: $strategyName returned usable URL =====")
                    url
                } else {
                    Log.d(TAG, "$strategyName timed out, failed, or returned invalid URL")
                    null
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "$strategyName cancelled")
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "$strategyName exception: ${e.message}")
                null
            }
        }

        val newPipeUrl = attempt("NewPipe extractor fallback", timeoutMs = STRATEGY_TIMEOUT_SLOW_MS) {
            tryGetStreamUrlFromNewPipe(videoId, mode = mode, targetVideoHeight = targetVideoHeight)
        }
        if (newPipeUrl != null) {
            return@withContext Result.success(newPipeUrl)
        }

        val iosUrl = attempt("iOS Innertube") {
            tryGetStreamUrl(videoId, createiOSContext(), mode = mode, targetVideoHeight = targetVideoHeight)
        }
        if (iosUrl != null) {
            return@withContext Result.success(iosUrl)
        }

        val androidUrl = attempt("Android Innertube") {
            tryGetStreamUrl(videoId, createAndroidContext(), mode = mode, targetVideoHeight = targetVideoHeight)
        }
        if (androidUrl != null) {
            return@withContext Result.success(androidUrl)
        }

        val tvUrl = attempt("TV Innertube", timeoutMs = STRATEGY_TIMEOUT_SLOW_MS) {
            tryGetStreamUrlTV(videoId, mode = mode, targetVideoHeight = targetVideoHeight)
        }
        if (tvUrl != null) {
            return@withContext Result.success(tvUrl)
        }

        val webUrl = attempt("Web Innertube") {
            tryGetStreamUrl(videoId, createWebContext(), mode = mode, targetVideoHeight = targetVideoHeight)
        }
        if (webUrl != null) {
            return@withContext Result.success(webUrl)
        }

        val outerTuneFallbackUrl = attempt("OuterTune-style client fallback") {
            tryGetStreamUrlFromOuterTuneClientFallbacks(videoId, mode = mode, targetVideoHeight = targetVideoHeight)
        }
        if (outerTuneFallbackUrl != null) {
            return@withContext Result.success(outerTuneFallbackUrl)
        }

        val watchUrl = attempt("Watch-page extraction", timeoutMs = STRATEGY_TIMEOUT_SLOW_MS) {
            tryGetStreamUrlFromWatchPage(videoId, mode = mode, targetVideoHeight = targetVideoHeight)
        }
        if (watchUrl != null) {
            return@withContext Result.success(watchUrl)
        }

        if (mode == StreamMode.AUDIO) {
            val invidiousUrl = attempt("Invidious fallback", timeoutMs = STRATEGY_TIMEOUT_SLOW_MS) {
                tryGetStreamUrlFromInvidious(videoId)
            }
            if (invidiousUrl != null) {
                return@withContext Result.success(invidiousUrl)
            }

            val pipedUrl = attempt("Piped fallback", timeoutMs = STRATEGY_TIMEOUT_SLOW_MS) {
                tryGetStreamUrlFromPiped(videoId)
            }
            if (pipedUrl != null) {
                return@withContext Result.success(pipedUrl)
            }
        }

        val videoInfoUrl = attempt("get_video_info fallback") {
            tryGetStreamUrlFromVideoInfo(videoId, mode = mode, targetVideoHeight = targetVideoHeight)
        }
        if (videoInfoUrl != null) {
            return@withContext Result.success(videoInfoUrl)
        }

        Log.e(TAG, "===== ALL STRATEGIES FAILED for $videoId =====")
        Result.failure(Exception("Unable to obtain stream URL from any source"))
    }
    
    private suspend fun tryGetStreamUrlTV(
        videoId: String,
        mode: StreamMode = StreamMode.AUDIO,
        targetVideoHeight: Int = 360
    ): String? {
        return try {
            println("  >>> Attempting TV player...")
            val context = createTVContext().deepCopy().apply {
                add("thirdParty", JsonObject().apply {
                    addProperty("embedUrl", "https://www.youtube.com/watch?v=$videoId")
                })
            }
            val body = JsonObject().apply {
                addProperty("videoId", videoId)
                addProperty("contentCheckOk", true)
                addProperty("racyCheckOk", true)
                add("context", context)
            }
            
            val response = innerTubeApi.player(
                body = body,
                apiKey = InnerTubeApi.TVHTML5_API_KEY,
                clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                clientVersion = "2.0",
                userAgent = "Mozilla/5.0 (PlayStation 4 5.55) AppleWebKit/601.2 (KHTML, like Gecko)",
                origin = "https://www.youtube.com",
                requestOrigin = "https://www.youtube.com",
                referer = "https://www.youtube.com/"
            )
            
            // Check playability status
            val playabilityStatus = response.getAsJsonObject("playabilityStatus")
            val status = playabilityStatus?.get("status")?.asString
            if (status != "OK") {
                val reason = playabilityStatus?.get("reason")?.asString
                println("      Playability status: $status, reason=$reason")
                Log.w(TAG, "TV Playability status: $status for $videoId, reason=$reason")
                return null
            }
            
            val url = parseStreamUrl(response, mode = mode, targetVideoHeight = targetVideoHeight)
            println("      Got URL: ${if (url == null) "NULL" else url.take(60) + "..."}")
            url
        } catch (e: Exception) {
            println("      Exception: ${e.message}")
            Log.w(TAG, "tryGetStreamUrlTV failed: ${e.message}")
            null
        }
    }
    
    /**
     * Tries to get stream URL from Piped API as a fallback.
     * Piped is an alternative YouTube frontend that provides direct stream URLs.
     */
    private suspend fun tryGetStreamUrlFromPiped(videoId: String): String? {
        println("  >>> Attempting Piped fallback...")
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        val gson = Gson()

        val allInstances = loadPipedInstances(client)
        val limitedInstances = allInstances.distinct().take(4)
        println("      Loaded ${allInstances.size} Piped instances, trying ${limitedInstances.size}")
        
        limitedInstances.forEach instanceLoop@ { instance ->
            try {
                println("      Trying instance: $instance")
                pipedStreamEndpointTemplates.forEach endpointLoop@ { endpointTemplate ->
                    val endpoint = endpointTemplate.format(videoId)
                    val url = instance.removeSuffix("/") + endpoint
                    println("        Endpoint: $endpoint -> $url")
                    
                    val request = okhttp3.Request.Builder()
                        .url(url)
                        .header("User-Agent", InnerTubeApi.WEB_USER_AGENT)
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            println("          HTTP ${response.code} - skipping")
                            return@endpointLoop
                        }

                        val responseBody = response.body?.string() ?: return@endpointLoop
                        val jsonObject = gson.fromJson(responseBody, JsonObject::class.java) ?: return@endpointLoop
                        val audioStreams = jsonObject.getAsJsonArray("audioStreams") ?: return@endpointLoop
                        if (audioStreams.size() == 0) {
                            println("          No audio streams in response")
                            return@endpointLoop
                        }

                        var bestUrl: String? = null
                        var highestBitrate = 0

                        for (i in 0 until audioStreams.size()) {
                            val streamObj = audioStreams.get(i).asJsonObject
                            val streamUrl = streamObj.get("url")?.asString ?: continue
                            val bitrate = streamObj.get("bitrate")?.asInt ?: 0
                            if (!isUsableStreamUrl(streamUrl)) continue

                            if (bitrate >= highestBitrate) {
                                highestBitrate = bitrate
                                bestUrl = streamUrl
                            }
                        }

                        if (!bestUrl.isNullOrBlank()) {
                            println("        SUCCESS: Got Piped URL at $highestBitrate bps")
                            Log.d(TAG, "Using Piped stream from $instance$endpoint at bitrate: $highestBitrate")
                            return bestUrl
                        }
                    }
                }
            } catch (e: Exception) {
                println("      Piped exception on $instance: ${e.message}")
                Log.w(TAG, "Piped fallback failed on $instance: ${e.message}")
            }
        }

        return null
    }

    private suspend fun tryGetStreamUrlFromInvidious(videoId: String): String? {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        val gson = Gson()

        for (instance in invidiousApiInstances) {
            try {
                val url = "${instance.removeSuffix("/")}/api/v1/videos/$videoId"
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", InnerTubeApi.WEB_USER_AGENT)
                    .build()

                var bestUrl: String? = null
                var highestBitrate = 0

                val response = client.newCall(request).execute()
                response.use { currentResponse ->
                    if (!currentResponse.isSuccessful) {
                        Log.d(TAG, "Invidious $instance returned HTTP ${currentResponse.code}")
                        return@use
                    }

                    val body = currentResponse.body?.string().orEmpty()
                    if (body.isBlank()) {
                        return@use
                    }

                    val root = gson.fromJson(body, JsonObject::class.java) ?: return@use
                    val adaptiveFormats = root.getAsJsonArray("adaptiveFormats") ?: return@use
                    if (adaptiveFormats.size() == 0) {
                        return@use
                    }

                    for (index in 0 until adaptiveFormats.size()) {
                        val format = adaptiveFormats.get(index).asJsonObject
                        val type = format.get("type")?.asString.orEmpty()
                        if (!type.contains("audio", ignoreCase = true)) {
                            continue
                        }

                        val streamUrl = format.get("url")?.asString ?: continue
                        if (!isUsableStreamUrl(streamUrl)) {
                            continue
                        }

                        val bitrate = when {
                            format.has("bitrate") -> format.get("bitrate")?.asInt ?: 0
                            format.has("bitrateKbps") -> (format.get("bitrateKbps")?.asInt ?: 0) * 1000
                            else -> 0
                        }

                        if (bitrate >= highestBitrate) {
                            highestBitrate = bitrate
                            bestUrl = streamUrl
                        }
                    }
                }

                if (!bestUrl.isNullOrBlank()) {
                    Log.d(TAG, "Using Invidious stream from $instance at bitrate=$highestBitrate")
                    return bestUrl
                }
            } catch (e: Exception) {
                Log.w(TAG, "Invidious fallback failed on $instance: ${e.message}")
            }
        }

        return null
    }
    
    private suspend fun tryGetStreamUrl(
        videoId: String,
        context: JsonObject,
        mode: StreamMode = StreamMode.AUDIO,
        targetVideoHeight: Int = 360
    ): String? {
        val clientName = context.getAsJsonObject("client")?.get("clientName")?.asString ?: "UNKNOWN"
        return try {
            Log.d(TAG, "Attempting $clientName player...")
            
            val body = JsonObject().apply {
                addProperty("videoId", videoId)
                addProperty("contentCheckOk", true)
                addProperty("racyCheckOk", true)
                add("context", context)
            }

            val clientConfig = resolvePlayerClientConfig(context)
            val response = innerTubeApi.player(
                body = body,
                apiKey = clientConfig.apiKey,
                clientName = clientConfig.headerClientName,
                clientVersion = clientConfig.clientVersion,
                userAgent = clientConfig.userAgent,
                origin = clientConfig.origin,
                requestOrigin = clientConfig.origin,
                referer = clientConfig.referer
            )
            
            Log.d(TAG, "$clientName response keys: ${response.keySet().joinToString(",")}")
            
            // Check playability status
            val playabilityStatus = response.getAsJsonObject("playabilityStatus")
            val status = playabilityStatus?.get("status")?.asString
            Log.d(TAG, "$clientName playability: $status")
            
            if (status != "OK") {
                val reason = playabilityStatus?.get("reason")?.asString
                val messages = playabilityStatus?.getAsJsonArray("messages")?.joinToString(";") { it.asString } ?: "none"
                Log.w(TAG, "$clientName ERROR - Status: $status, Reason: $reason, Messages: $messages")
                return null
            }
            
            // Check if streamingData exists
            val streamingData = response.getAsJsonObject("streamingData")
            if (streamingData == null) {
                Log.w(TAG, "$clientName returned OK but NO streamingData! Checking alternatives...")
                Log.d(TAG, "$clientName full response preview: ${response.toString().take(300)}")
                return null
            }
            
            val url = parseStreamUrl(response, mode = mode, targetVideoHeight = targetVideoHeight)
            Log.d(TAG, "$clientName parseStreamUrl returned: ${if (url == null) "NULL" else url.take(100)}")
            url
        } catch (e: Exception) {
            Log.e(TAG, "$clientName exception: ${e.message}", e)
            null
        }
    }

    private suspend fun tryGetStreamUrlFromOuterTuneClientFallbacks(
        videoId: String,
        mode: StreamMode = StreamMode.AUDIO,
        targetVideoHeight: Int = 360
    ): String? {
        val contexts = listOf(
            "ANDROID_VR" to createAndroidVrContext(),
            "ANDROID" to createAndroidClientContext(),
            "WEB_CREATOR" to createWebCreatorContext(),
            "IOS" to createiOSContext()
        )

        var firstCandidate: String? = null

        for ((label, context) in contexts) {
            val url = tryGetStreamUrl(videoId, context, mode = mode, targetVideoHeight = targetVideoHeight) ?: continue
            if (firstCandidate == null) {
                firstCandidate = url
            }

            if (validateStreamUrlStatus(url)) {
                Log.d(TAG, "OuterTune-style fallback validated stream with client=$label")
                return url
            }

            Log.d(TAG, "OuterTune-style candidate failed quick validation for client=$label")
        }

        return firstCandidate
    }

    private fun validateStreamUrlStatus(url: String): Boolean {
        return try {
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Range", "bytes=0-1")
                .header("User-Agent", InnerTubeApi.WEB_USER_AGENT)
                .get()
                .build()

            extractorHttpClient.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 206
            }
        } catch (e: Exception) {
            Log.d(TAG, "Stream status validation failed: ${e.message}")
            false
        }
    }

    private suspend fun tryGetStreamUrlFromVideoInfo(
        videoId: String,
        mode: StreamMode = StreamMode.AUDIO,
        targetVideoHeight: Int = 360
    ): String? {
        return try {
            println("  >>> Attempting get_video_info fallback...")
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val request = okhttp3.Request.Builder()
                .url("https://www.youtube.com/get_video_info?video_id=$videoId&el=detailpage&hl=en")
                .header("User-Agent", InnerTubeApi.WEB_USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    println("      HTTP ${response.code} - request failed")
                    Log.w(TAG, "get_video_info failed: ${response.code}")
                    return null
                }

                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    println("      Empty response body")
                    return null
                }

                val pairs = body
                    .split("&")
                    .mapNotNull { entry ->
                        val parts = entry.split("=", limit = 2)
                        if (parts.size != 2) {
                            null
                        } else {
                            parts[0] to URLDecoder.decode(parts[1], "UTF-8")
                        }
                    }
                    .toMap()

                val playerResponseRaw = pairs["player_response"] ?: return null
                val playerResponse = Gson().fromJson(playerResponseRaw, JsonObject::class.java) ?: return null
                parseStreamUrl(playerResponse, mode = mode, targetVideoHeight = targetVideoHeight)
            }
        } catch (e: Exception) {
            Log.w(TAG, "tryGetStreamUrlFromVideoInfo failed: ${e.message}")
            null
        }
    }

    private suspend fun tryGetStreamUrlFromWatchPage(
        videoId: String,
        mode: StreamMode = StreamMode.AUDIO,
        targetVideoHeight: Int = 360
    ): String? {
        println("  >>> Attempting watch-page HTML parsing...")
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val watchUrls = listOf(
            "https://www.youtube.com/watch?v=$videoId&hl=en",
            "https://music.youtube.com/watch?v=$videoId"
        )

        for (url in watchUrls) {
            try {
                println("      Trying URL: $url")
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", InnerTubeApi.WEB_USER_AGENT)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        println("        HTTP ${response.code} - failed")
                        return@use
                    }
                    val html = response.body?.string().orEmpty()
                    if (html.isBlank()) {
                        println("        Empty HTML response")
                        return@use
                    }

                    println("        Got ${html.length} bytes of HTML")

                    val playerScriptUrl = parsePlayerScriptUrlFromHtml(html)
                    if (!playerScriptUrl.isNullOrBlank()) {
                        ensureSignatureDecipherPlan(playerScriptUrl, client)
                    }

                    val playerResponse = parsePlayerResponseFromHtml(html)
                    if (playerResponse == null) {
                        println("        Failed to parse ytInitialPlayerResponse from HTML")
                        return@use
                    }
                    
                    val streamUrl = parseStreamUrl(playerResponse, mode = mode, targetVideoHeight = targetVideoHeight)
                    println("        parseStreamUrl returned: ${if (streamUrl == null) "NULL" else streamUrl.take(60) + "..."}")
                    
                    if (isUsableStreamUrl(streamUrl)) {
                        println("      SUCCESS: Got usable URL from watch page")
                        return streamUrl
                    }
                }
            } catch (e: Exception) {
                println("      Exception: ${e.message}")
                Log.w(TAG, "Watch page fallback failed for $url: ${e.message}")
            }
        }

        println("      All watch page URLs exhausted")
        return null
    }

    private suspend fun tryGetStreamUrlFromNewPipe(
        videoId: String,
        mode: StreamMode = StreamMode.AUDIO,
        targetVideoHeight: Int = 360
    ): String? {
        return try {
            ensureNewPipeInitialized()
            val watchUrl = "https://www.youtube.com/watch?v=$videoId"
            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, watchUrl)

            if (mode == StreamMode.VIDEO && isUsableStreamUrl(streamInfo.hlsUrl)) {
                Log.d(TAG, "Using NewPipe HLS manifest for video mode target=${targetVideoHeight}p")
                return streamInfo.hlsUrl
            }

            val bestAudioStream = selectBestAudioStream(streamInfo.audioStreams)
            val bestAudioUrl = bestAudioStream?.url
            if (isUsableStreamUrl(bestAudioUrl)) {
                Log.d(TAG, "Using NewPipe stream at bitrate=${resolveAudioBitrate(bestAudioStream)}")
                return bestAudioUrl
            }

            if (isUsableStreamUrl(streamInfo.hlsUrl)) {
                Log.d(TAG, "Using NewPipe HLS manifest fallback")
                return streamInfo.hlsUrl
            }

            if (isUsableStreamUrl(streamInfo.dashMpdUrl)) {
                Log.d(TAG, "Using NewPipe DASH manifest fallback")
                return streamInfo.dashMpdUrl
            }

            Log.w(TAG, "NewPipe returned no usable URLs for $videoId")
            null
        } catch (e: Exception) {
            Log.w(TAG, "NewPipe fallback failed for $videoId: ${e.message}")
            null
        }
    }

    private fun ensureNewPipeInitialized() {
        if (newPipeInitialized) return

        synchronized(newPipeInitLock) {
            if (newPipeInitialized) return
            NewPipe.init(NewPipeOkHttpDownloader(extractorHttpClient))
            newPipeInitialized = true
            Log.d(TAG, "Initialized NewPipe extractor")
        }
    }

    private fun selectBestAudioStream(audioStreams: List<AudioStream>?): AudioStream? {
        if (audioStreams.isNullOrEmpty()) return null
        return audioStreams
            .filter { stream -> isUsableStreamUrl(stream.url) }
            .maxByOrNull { stream -> resolveAudioBitrate(stream) }
    }

    private fun resolveAudioBitrate(stream: AudioStream?): Int {
        if (stream == null) return 0
        return maxOf(stream.averageBitrate, stream.bitrate, 0)
    }

    private fun parsePlayerResponseFromHtml(html: String): JsonObject? {
        val assignmentMarker = "ytInitialPlayerResponse ="
        val assignmentIndex = html.indexOf(assignmentMarker)
        if (assignmentIndex >= 0) {
            val jsonStart = html.indexOf('{', assignmentIndex + assignmentMarker.length)
            val jsonText = extractJsonObjectFromText(html, jsonStart)
            if (!jsonText.isNullOrBlank()) {
                return Gson().fromJson(jsonText, JsonObject::class.java)
            }
        }

        val embeddedMarker = "\"ytInitialPlayerResponse\":"
        val embeddedIndex = html.indexOf(embeddedMarker)
        if (embeddedIndex >= 0) {
            val jsonStart = html.indexOf('{', embeddedIndex + embeddedMarker.length)
            val jsonText = extractJsonObjectFromText(html, jsonStart)
            if (!jsonText.isNullOrBlank()) {
                return Gson().fromJson(jsonText, JsonObject::class.java)
            }
        }

        return null
    }

    private fun parsePlayerScriptUrlFromHtml(html: String): String? {
        val patterns = listOf(
            Regex("\"jsUrl\"\\s*:\\s*\"([^\"]+base\\.js[^\"]*)\""),
            Regex("\"PLAYER_JS_URL\"\\s*:\\s*\"([^\"]+base\\.js[^\"]*)\""),
            Regex("\"js\"\\s*:\\s*\"([^\"]+base\\.js[^\"]*)\"")
        )

        for (pattern in patterns) {
            val match = pattern.find(html) ?: continue
            val rawUrl = match.groupValues.getOrNull(1).orEmpty()
            val decodedUrl = rawUrl
                .replace("\\/", "/")
                .replace("\\u0026", "&")
                .replace("\\u002F", "/")
                .trim()

            if (decodedUrl.isBlank()) continue

            return when {
                decodedUrl.startsWith("http://") || decodedUrl.startsWith("https://") -> decodedUrl
                decodedUrl.startsWith("//") -> "https:$decodedUrl"
                decodedUrl.startsWith("/") -> "https://www.youtube.com$decodedUrl"
                else -> "https://www.youtube.com/$decodedUrl"
            }
        }

        return null
    }

    private fun ensureSignatureDecipherPlan(playerScriptUrl: String, client: okhttp3.OkHttpClient) {
        val cachedPlan = signatureDecipherPlanCache
        if (cachedPlan != null && cachedPlan.playerScriptUrl == playerScriptUrl && cachedPlan.operations.isNotEmpty()) {
            return
        }

        try {
            val request = okhttp3.Request.Builder()
                .url(playerScriptUrl)
                .header("User-Agent", InnerTubeApi.WEB_USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.d(TAG, "Player script request failed: HTTP ${response.code}")
                    return
                }

                val script = response.body?.string().orEmpty()
                if (script.isBlank()) {
                    return
                }

                val functionName = extractSignatureFunctionName(script) ?: return
                val functionBody = extractFunctionBody(script, functionName) ?: return

                val helperObjectName = extractHelperObjectName(functionBody)
                val helperMethods = if (!helperObjectName.isNullOrBlank()) {
                    extractHelperMethods(script, helperObjectName)
                } else {
                    emptyMap()
                }

                val operations = extractSignatureOperations(functionBody, helperObjectName, helperMethods)
                if (operations.isEmpty()) {
                    Log.d(TAG, "No signature operations extracted from player script")
                    return
                }

                signatureDecipherPlanCache = SignatureDecipherPlan(
                    playerScriptUrl = playerScriptUrl,
                    operations = operations
                )
                Log.d(TAG, "Loaded signature decipher plan with ${operations.size} operations")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to build signature decipher plan: ${e.message}")
        }
    }

    private fun extractSignatureFunctionName(script: String): String? {
        val patterns = listOf(
            Regex("\\.sig\\|\\|([\\w$]+)\\("),
            Regex("[\"']signature[\"']\\s*,\\s*([\\w$]+)\\("),
            Regex("\\bc&&\\(c=([\\w$]+)\\(decodeURIComponent\\(c\\)\\)\\)")
        )

        return patterns.firstNotNullOfOrNull { regex ->
            regex.find(script)?.groupValues?.getOrNull(1)
        }
    }

    private fun extractFunctionBody(script: String, functionName: String): String? {
        val escapedFunctionName = Regex.escape(functionName)
        val definitions = listOf(
            Regex("function\\s+$escapedFunctionName\\s*\\([\\w$]+\\)\\s*\\{"),
            Regex("(?:var|let|const)\\s+$escapedFunctionName\\s*=\\s*function\\s*\\([\\w$]+\\)\\s*\\{"),
            Regex("$escapedFunctionName\\s*=\\s*function\\s*\\([\\w$]+\\)\\s*\\{")
        )

        for (definition in definitions) {
            val match = definition.find(script) ?: continue
            val braceStart = script.indexOf('{', match.range.first)
            val block = extractBracedBlock(script, braceStart) ?: continue
            return block.removePrefix("{").removeSuffix("}")
        }

        return null
    }

    private fun extractHelperObjectName(functionBody: String): String? {
        val operationCall = Regex("([\\w$]{2,})\\.([\\w$]{2,})\\([\\w$]+(?:,\\d+)?\\)")
        return operationCall.find(functionBody)?.groupValues?.getOrNull(1)
    }

    private fun extractHelperMethods(
        script: String,
        helperObjectName: String
    ): Map<String, SignatureOperationType> {
        val escapedHelperObjectName = Regex.escape(helperObjectName)
        val definitions = listOf(
            Regex("(?:var|let|const)\\s+$escapedHelperObjectName\\s*=\\s*\\{"),
            Regex("$escapedHelperObjectName\\s*=\\s*\\{")
        )

        var objectBody: String? = null
        for (definition in definitions) {
            val match = definition.find(script) ?: continue
            val braceStart = script.indexOf('{', match.range.first)
            val block = extractBracedBlock(script, braceStart)
            if (!block.isNullOrBlank()) {
                objectBody = block.removePrefix("{").removeSuffix("}")
                break
            }
        }

        if (objectBody.isNullOrBlank()) {
            return emptyMap()
        }

        val methodMap = mutableMapOf<String, SignatureOperationType>()

        val functionPropertyPattern = Regex("([\\w$]+)\\s*:\\s*function\\s*\\([\\w$]+(?:,[\\w$]+)?\\)\\s*\\{")
        functionPropertyPattern.findAll(objectBody).forEach { match ->
            val methodName = match.groupValues.getOrNull(1).orEmpty()
            val braceStart = objectBody.indexOf('{', match.range.first)
            val methodBlock = extractBracedBlock(objectBody, braceStart)
            if (!methodBlock.isNullOrBlank()) {
                val operationType = inferSignatureOperationType(methodBlock)
                if (operationType != null) {
                    methodMap[methodName] = operationType
                }
            }
        }

        val shorthandMethodPattern = Regex("([\\w$]+)\\s*\\([\\w$]+(?:,[\\w$]+)?\\)\\s*\\{")
        shorthandMethodPattern.findAll(objectBody).forEach { match ->
            val methodName = match.groupValues.getOrNull(1).orEmpty()
            if (methodMap.containsKey(methodName)) return@forEach

            val braceStart = objectBody.indexOf('{', match.range.first)
            val methodBlock = extractBracedBlock(objectBody, braceStart)
            if (!methodBlock.isNullOrBlank()) {
                val operationType = inferSignatureOperationType(methodBlock)
                if (operationType != null) {
                    methodMap[methodName] = operationType
                }
            }
        }

        return methodMap
    }

    private fun inferSignatureOperationType(methodBody: String): SignatureOperationType? {
        val normalized = methodBody.replace("\n", " ")
        return when {
            normalized.contains("reverse()") -> SignatureOperationType.REVERSE
            normalized.contains("splice(0,") || normalized.contains(".slice(") -> SignatureOperationType.SLICE
            (normalized.contains("[0]") && normalized.contains("%") && normalized.contains(".length")) ||
                (normalized.contains("var") && normalized.contains("[0]") && normalized.contains("=")) -> {
                SignatureOperationType.SWAP
            }
            else -> null
        }
    }

    private fun extractSignatureOperations(
        functionBody: String,
        helperObjectName: String?,
        helperMethods: Map<String, SignatureOperationType>
    ): List<SignatureOperation> {
        val operations = mutableListOf<SignatureOperation>()

        if (!helperObjectName.isNullOrBlank() && helperMethods.isNotEmpty()) {
            val callPattern = Regex("${Regex.escape(helperObjectName)}\\.([\\w$]+)\\([\\w$]+(?:,(\\d+))?\\)")
            callPattern.findAll(functionBody).forEach { match ->
                val methodName = match.groupValues.getOrNull(1).orEmpty()
                val operationType = helperMethods[methodName] ?: return@forEach
                val argument = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
                operations.add(SignatureOperation(operationType, argument))
            }
        }

        if (operations.isNotEmpty()) {
            return operations
        }

        val simpleOperations = mutableListOf<Pair<Int, SignatureOperation>>()

        Regex("\\.reverse\\(\\)").findAll(functionBody).forEach { match ->
            simpleOperations.add(match.range.first to SignatureOperation(SignatureOperationType.REVERSE))
        }

        Regex("\\.splice\\(0,(\\d+)\\)").findAll(functionBody).forEach { match ->
            val argument = match.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
            simpleOperations.add(match.range.first to SignatureOperation(SignatureOperationType.SLICE, argument))
        }

        Regex("\\.slice\\((\\d+)\\)").findAll(functionBody).forEach { match ->
            val argument = match.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
            simpleOperations.add(match.range.first to SignatureOperation(SignatureOperationType.SLICE, argument))
        }

        Regex("\\[0\\]\\s*=\\s*[\\w$]+\\[(\\d+)\\s*%\\s*[\\w$]+\\.length\\]").findAll(functionBody).forEach { match ->
            val argument = match.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
            simpleOperations.add(match.range.first to SignatureOperation(SignatureOperationType.SWAP, argument))
        }

        return simpleOperations
            .sortedBy { it.first }
            .map { it.second }
    }

    private fun decipherSignature(obfuscatedSignature: String): String? {
        val plan = signatureDecipherPlanCache ?: return null
        if (plan.operations.isEmpty()) return null

        val chars = obfuscatedSignature.toMutableList()
        if (chars.isEmpty()) return null

        plan.operations.forEach { operation ->
            when (operation.type) {
                SignatureOperationType.REVERSE -> chars.reverse()
                SignatureOperationType.SLICE -> {
                    val count = operation.argument.coerceAtLeast(0)
                    repeat(count.coerceAtMost(chars.size)) {
                        chars.removeAt(0)
                    }
                }
                SignatureOperationType.SWAP -> {
                    if (chars.isNotEmpty()) {
                        val index = operation.argument.mod(chars.size)
                        val first = chars[0]
                        chars[0] = chars[index]
                        chars[index] = first
                    }
                }
            }
        }

        return chars.joinToString(separator = "")
    }

    private fun extractJsonObjectFromText(text: String, startIndex: Int): String? {
        if (startIndex < 0 || startIndex >= text.length || text[startIndex] != '{') return null

        var depth = 0
        var inString = false
        var escaped = false

        for (index in startIndex until text.length) {
            val ch = text[index]

            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }

            when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return text.substring(startIndex, index + 1)
                    }
                }
            }
        }

        return null
    }

    private fun extractBracedBlock(text: String, startIndex: Int): String? {
        if (startIndex < 0 || startIndex >= text.length || text[startIndex] != '{') return null

        var depth = 0
        var escaped = false
        var stringDelimiter: Char? = null

        for (index in startIndex until text.length) {
            val currentChar = text[index]

            if (stringDelimiter != null) {
                if (escaped) {
                    escaped = false
                } else if (currentChar == '\\') {
                    escaped = true
                } else if (currentChar == stringDelimiter) {
                    stringDelimiter = null
                }
                continue
            }

            when (currentChar) {
                '\'', '"' -> stringDelimiter = currentChar
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return text.substring(startIndex, index + 1)
                    }
                }
            }
        }

        return null
    }

    private fun loadPipedInstances(client: okhttp3.OkHttpClient): List<String> {
        val now = System.currentTimeMillis()
        pipedInstancesCache?.let { (instances, timestamp) ->
            if ((now - timestamp) <= PIPED_INSTANCE_CACHE_TTL_MS && instances.isNotEmpty()) {
                return instances
            }
        }

        val discoveredInstances = try {
            val request = okhttp3.Request.Builder()
                .url(PIPED_INSTANCE_LIST_URL)
                .header("User-Agent", InnerTubeApi.WEB_USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emptyList()
                } else {
                    val markdown = response.body?.string().orEmpty()
                    Regex("\\|\\s*[^|]+\\|\\s*(https://[^|\\s]+)\\s*\\|")
                        .findAll(markdown)
                        .mapNotNull { match -> match.groupValues.getOrNull(1) }
                        .filter { url -> url.contains("pipedapi") || url.contains("api.piped") || url.contains("piped-api") }
                        .toList()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch dynamic Piped instances: ${e.message}")
            emptyList()
        }

        val mergedInstances = (pipedApiInstances + discoveredInstances)
            .map { it.trim().removeSuffix("/") }
            .filter { it.startsWith("https://") }
            .distinct()

        pipedInstancesCache = mergedInstances to now
        return mergedInstances
    }
    
    suspend fun getRelatedSongs(videoId: String): Result<List<SongItem>> = withContext(Dispatchers.IO) {
        try {
            val body = JsonObject().apply {
                addProperty("videoId", videoId)
                addProperty("isAudioOnly", true)
                add("context", createContext())
            }
            
            val response = innerTubeApi.next(body)
            val relatedSongs = parseRelatedSongs(response)
            Result.success(relatedSongs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun collectRendererObjects(
        element: JsonElement?,
        rendererKey: String,
        destination: MutableList<JsonObject>
    ) {
        if (element == null || element.isJsonNull) return

        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject

                if (obj.has(rendererKey) && obj.get(rendererKey).isJsonObject) {
                    destination.add(obj.getAsJsonObject(rendererKey))
                }

                obj.entrySet().forEach { (_, value) ->
                    collectRendererObjects(value, rendererKey, destination)
                }
            }

            element.isJsonArray -> {
                element.asJsonArray.forEach { child ->
                    collectRendererObjects(child, rendererKey, destination)
                }
            }
        }
    }

    private fun readTextNode(node: JsonObject?): String? {
        if (node == null) return null

        val simpleText = node.get("simpleText")?.asString?.trim()
        if (!simpleText.isNullOrBlank()) {
            return simpleText
        }

        val runs = node.getAsJsonArray("runs")
        if (runs != null && runs.size() > 0) {
            val text = runs.joinToString(separator = "") { run ->
                run.asJsonObject?.get("text")?.asString.orEmpty()
            }.trim()
            if (text.isNotBlank()) {
                return text
            }
        }

        return null
    }

    private fun extractThumbnailUrl(renderer: JsonObject): String? {
        val direct = listOfNotNull(
            renderer.getAsJsonObject("thumbnail")
                ?.getAsJsonObject("musicThumbnailRenderer")
                ?.getAsJsonObject("thumbnail")
                ?.getAsJsonArray("thumbnails")
                ?.lastOrNull()?.asJsonObject
                ?.get("url")?.asString,
            renderer.getAsJsonObject("thumbnailRenderer")
                ?.getAsJsonObject("musicThumbnailRenderer")
                ?.getAsJsonObject("thumbnail")
                ?.getAsJsonArray("thumbnails")
                ?.lastOrNull()?.asJsonObject
                ?.get("url")?.asString,
            renderer.getAsJsonObject("thumbnail")
                ?.getAsJsonObject("croppedSquareThumbnailRenderer")
                ?.getAsJsonObject("thumbnail")
                ?.getAsJsonArray("thumbnails")
                ?.lastOrNull()?.asJsonObject
                ?.get("url")?.asString
        ).firstOrNull { !it.isNullOrBlank() }

        if (!direct.isNullOrBlank()) return direct

        val musicThumbRenderers = mutableListOf<JsonObject>()
        collectRendererObjects(renderer, "musicThumbnailRenderer", musicThumbRenderers)
        musicThumbRenderers.firstNotNullOfOrNull { thumbRenderer ->
            thumbRenderer.getAsJsonObject("thumbnail")
                ?.getAsJsonArray("thumbnails")
                ?.lastOrNull()?.asJsonObject
                ?.get("url")?.asString
                ?.takeIf { it.isNotBlank() }
        }?.let { return it }

        val croppedThumbRenderers = mutableListOf<JsonObject>()
        collectRendererObjects(renderer, "croppedSquareThumbnailRenderer", croppedThumbRenderers)
        return croppedThumbRenderers.firstNotNullOfOrNull { thumbRenderer ->
            thumbRenderer.getAsJsonObject("thumbnail")
                ?.getAsJsonArray("thumbnails")
                ?.lastOrNull()?.asJsonObject
                ?.get("url")?.asString
                ?.takeIf { it.isNotBlank() }
        }
    }

    private fun extractVideoIdFromRenderer(renderer: JsonObject): String? {
        val directVideoId = renderer.getAsJsonObject("playlistItemData")
            ?.get("videoId")?.asString
            ?: renderer.getAsJsonObject("navigationEndpoint")
                ?.getAsJsonObject("watchEndpoint")
                ?.get("videoId")?.asString
            ?: renderer.getAsJsonObject("overlay")
                ?.getAsJsonObject("musicItemThumbnailOverlayRenderer")
                ?.getAsJsonObject("content")
                ?.getAsJsonObject("musicPlayButtonRenderer")
                ?.getAsJsonObject("playNavigationEndpoint")
                ?.getAsJsonObject("watchEndpoint")
                ?.get("videoId")?.asString
            ?: renderer.getAsJsonArray("flexColumns")
                ?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                ?.getAsJsonObject("text")
                ?.getAsJsonArray("runs")
                ?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("navigationEndpoint")
                ?.getAsJsonObject("watchEndpoint")
                ?.get("videoId")?.asString

        if (!directVideoId.isNullOrBlank()) {
            return directVideoId
        }

        val watchEndpoints = mutableListOf<JsonObject>()
        collectRendererObjects(renderer, "watchEndpoint", watchEndpoints)
        return watchEndpoints.firstNotNullOfOrNull { endpoint ->
            endpoint.get("videoId")?.asString?.takeIf { it.isNotBlank() }
        }
    }

    private fun extractBrowseEndpointFromRenderer(renderer: JsonObject): JsonObject? {
        renderer.getAsJsonObject("navigationEndpoint")
            ?.getAsJsonObject("browseEndpoint")
            ?.let { return it }

        renderer.getAsJsonArray("flexColumns")
            ?.firstOrNull()?.asJsonObject
            ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
            ?.getAsJsonObject("text")
            ?.getAsJsonArray("runs")
            ?.firstOrNull()?.asJsonObject
            ?.getAsJsonObject("navigationEndpoint")
            ?.getAsJsonObject("browseEndpoint")
            ?.let { return it }

        val browseEndpoints = mutableListOf<JsonObject>()
        collectRendererObjects(renderer, "browseEndpoint", browseEndpoints)
        return browseEndpoints.firstOrNull { endpoint ->
            endpoint.get("browseId")?.asString?.isNotBlank() == true
        }
    }

    private fun extractMusicPageType(browseEndpoint: JsonObject?): String? {
        return browseEndpoint
            ?.getAsJsonObject("browseEndpointContextSupportedConfigs")
            ?.getAsJsonObject("browseEndpointContextMusicConfig")
            ?.get("pageType")?.asString
    }

    private fun extractResponsiveTitle(renderer: JsonObject): String? {
        val flexColumns = renderer.getAsJsonArray("flexColumns") ?: return null
        if (flexColumns.size() == 0) return null

        return flexColumns
            .firstOrNull()?.asJsonObject
            ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
            ?.getAsJsonObject("text")
            ?.let { readTextNode(it) }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractResponsiveArtistText(renderer: JsonObject): String {
        val flexColumns = renderer.getAsJsonArray("flexColumns") ?: return ""
        if (flexColumns.size() <= 1) return ""

        val parts = mutableListOf<String>()
        for (index in 1 until flexColumns.size()) {
            val text = flexColumns.get(index)?.asJsonObject
                ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                ?.getAsJsonObject("text")
                ?.let { readTextNode(it) }
                ?.trim()
                .orEmpty()
            if (text.isNotBlank()) {
                parts.add(text)
            }
        }

        return parts.joinToString(" • ")
    }

    private fun extractRendererTitle(renderer: JsonObject): String? {
        return extractResponsiveTitle(renderer)
            ?: readTextNode(renderer.getAsJsonObject("title"))
            ?: readTextNode(renderer.getAsJsonObject("text"))
    }

    private fun parseResponsiveSongItem(renderer: JsonObject): SongItem? {
        val videoId = extractVideoIdFromRenderer(renderer) ?: return null
        val title = extractResponsiveTitle(renderer) ?: return null
        val artistsText = extractResponsiveArtistText(renderer)
        val durationMs = parseDurationFromFixedColumns(renderer.getAsJsonArray("fixedColumns"))
            ?: parseDurationMs(artistsText)
            ?: parseDurationMs(title)

        return createSongItemIfEligible(
            id = videoId,
            title = title,
            artistsText = artistsText,
            thumbnailUrl = extractThumbnailUrl(renderer),
            durationMs = durationMs
        )
    }

    private fun parseTwoRowSongItem(renderer: JsonObject): SongItem? {
        val videoId = renderer.getAsJsonObject("navigationEndpoint")
            ?.getAsJsonObject("watchEndpoint")
            ?.get("videoId")?.asString
            ?: extractVideoIdFromRenderer(renderer)
            ?: return null

        val title = readTextNode(renderer.getAsJsonObject("title")) ?: return null
        val subtitle = readTextNode(renderer.getAsJsonObject("subtitle")).orEmpty()

        return createSongItemIfEligible(
            id = videoId,
            title = title,
            artistsText = subtitle,
            thumbnailUrl = extractThumbnailUrl(renderer),
            durationMs = parseDurationMs(subtitle) ?: parseDurationMs(title)
        )
    }

    private fun parsePlaylistPanelSongItem(renderer: JsonObject): SongItem? {
        val videoId = renderer.get("videoId")?.asString
            ?: renderer.getAsJsonObject("navigationEndpoint")
                ?.getAsJsonObject("watchEndpoint")
                ?.get("videoId")?.asString
            ?: return null

        val title = readTextNode(renderer.getAsJsonObject("title")) ?: return null
        val artistText = readTextNode(renderer.getAsJsonObject("shortBylineText"))
            ?: readTextNode(renderer.getAsJsonObject("longBylineText"))
            ?: ""
        val lengthText = readTextNode(renderer.getAsJsonObject("lengthText"))
        val thumbnailUrl = renderer.getAsJsonObject("thumbnail")
            ?.getAsJsonArray("thumbnails")
            ?.lastOrNull()?.asJsonObject
            ?.get("url")?.asString

        return createSongItemIfEligible(
            id = videoId,
            title = title,
            artistsText = artistText,
            thumbnailUrl = thumbnailUrl,
            durationMs = parseDurationMs(lengthText ?: artistText)
        )
    }

    private fun parseMoodGenreItem(renderer: JsonObject): MoodGenreItem? {
        val title = readTextNode(renderer.getAsJsonObject("buttonText"))
            ?: readTextNode(renderer.getAsJsonObject("title"))
            ?: readTextNode(renderer.getAsJsonObject("text"))
            ?: return null

        val browseEndpoint = renderer.getAsJsonObject("clickCommand")
            ?.getAsJsonObject("browseEndpoint")
            ?: renderer.getAsJsonObject("navigationEndpoint")
                ?.getAsJsonObject("browseEndpoint")
            ?: return null

        val params = browseEndpoint.get("params")?.asString?.trim().orEmpty()
        if (params.isBlank()) return null

        if (title.equals("More", ignoreCase = true) || title.equals("See all", ignoreCase = true)) {
            return null
        }

        val browseId = browseEndpoint.get("browseId")?.asString.orEmpty()
        if (browseId.isNotBlank()) {
            val looksLikeMoodBrowse = browseId.contains("mood", ignoreCase = true) ||
                browseId.contains("genre", ignoreCase = true) ||
                browseId.contains("category", ignoreCase = true)

            if (!looksLikeMoodBrowse) {
                return null
            }
        }

        val thumbnailUrl = extractThumbnailUrl(renderer)
        val color = extractMoodGenreColor(renderer)
        return MoodGenreItem(
            title = title,
            params = params,
            thumbnailUrl = thumbnailUrl,
            color = color
        )
    }

    private fun extractMoodGenreColor(renderer: JsonObject): String? {
        val solid = renderer.getAsJsonObject("solid")
        val colorValue = listOf("leftStripeColor", "middleStripeColor", "rightStripeColor", "backgroundColor")
            .firstNotNullOfOrNull { key ->
                val node = solid?.get(key) ?: renderer.get(key)
                when {
                    node == null || node.isJsonNull -> null
                    !node.isJsonPrimitive -> null
                    node.asJsonPrimitive.isNumber -> node.asLong
                    node.asJsonPrimitive.isString -> node.asString.toLongOrNull()
                    else -> null
                }
            } ?: return null

        val normalized = colorValue and 0xFFFFFFFFL
        return String.format("#%08X", normalized)
    }

    private fun addMoodGenreItem(
        item: MoodGenreItem,
        moodsAndGenres: MutableList<MoodGenreItem>
    ) {
        val alreadyExists = moodsAndGenres.any { existing ->
            existing.params == item.params || existing.title.equals(item.title, ignoreCase = true)
        }
        if (!alreadyExists) {
            moodsAndGenres.add(item)
        }
    }

    private fun parseBrowseCollectionItem(renderer: JsonObject): Any? {
        val browseEndpoint = extractBrowseEndpointFromRenderer(renderer) ?: return null
        val browseId = browseEndpoint.get("browseId")?.asString ?: return null
        val pageType = extractMusicPageType(browseEndpoint)
        val title = extractRendererTitle(renderer) ?: return null
        val thumbnailUrl = extractThumbnailUrl(renderer)

        return when (pageType) {
            "MUSIC_PAGE_TYPE_ARTIST" -> ArtistItem(browseId, title, thumbnailUrl)
            "MUSIC_PAGE_TYPE_ALBUM" -> AlbumItem(browseId, title, thumbnailUrl = thumbnailUrl)
            "MUSIC_PAGE_TYPE_PLAYLIST" -> PlaylistItem(browseId, title, thumbnailUrl = thumbnailUrl)
            else -> null
        }
    }

    private fun extractHomeShelfTitle(shelf: JsonObject): String {
        return shelf.getAsJsonObject("header")
            ?.getAsJsonObject("musicCarouselShelfBasicHeaderRenderer")
            ?.getAsJsonObject("title")
            ?.let { readTextNode(it) }
            ?: shelf.getAsJsonObject("header")
                ?.getAsJsonObject("musicResponsiveHeaderRenderer")
                ?.getAsJsonObject("title")
                ?.let { readTextNode(it) }
            ?: shelf.getAsJsonObject("title")
                ?.let { readTextNode(it) }
            ?: ""
    }

    private fun addHomeSong(
        song: SongItem,
        shelfTitleLower: String,
        quickPicks: MutableList<SongItem>,
        trendingSongs: MutableList<SongItem>
    ) {
        if (quickPicks.any { it.id == song.id } || trendingSongs.any { it.id == song.id }) {
            return
        }

        val isTrendingShelf = shelfTitleLower.contains("trend") ||
            shelfTitleLower.contains("chart") ||
            shelfTitleLower.contains("top") ||
            shelfTitleLower.contains("hot")

        val isQuickPickShelf = shelfTitleLower.contains("quick") ||
            shelfTitleLower.contains("listen again") ||
            shelfTitleLower.contains("for you") ||
            shelfTitleLower.contains("recommended") ||
            shelfTitleLower.contains("because")

        when {
            isTrendingShelf -> trendingSongs.add(song)
            isQuickPickShelf || quickPicks.size < 20 -> quickPicks.add(song)
            else -> trendingSongs.add(song)
        }
    }
    
    fun getSearchHistory(): Flow<List<SearchHistory>> = searchHistoryDao.getSearchHistory()
    
    suspend fun deleteSearchHistory(query: String) = searchHistoryDao.delete(query)
    
    suspend fun clearSearchHistory() = searchHistoryDao.clearAll()
    
    // Parsing helpers
    private fun parseSearchResult(response: JsonObject, filter: SearchFilter): SearchResult {
        val songs = mutableListOf<SongItem>()
        val artists = mutableListOf<ArtistItem>()
        val albums = mutableListOf<AlbumItem>()
        val playlists = mutableListOf<PlaylistItem>()
        val videos = mutableListOf<VideoItem>()
        
        try {
            // Try tabbedSearchResultsRenderer first (newer format)
            var contents = response.getAsJsonObject("contents")
                ?.getAsJsonObject("tabbedSearchResultsRenderer")
                ?.getAsJsonArray("tabs")
                ?.get(0)?.asJsonObject
                ?.getAsJsonObject("tabRenderer")
                ?.getAsJsonObject("content")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents")
            
            // Fallback to searchResultsRenderer (older format)
            if (contents == null) {
                contents = response.getAsJsonObject("contents")
                    ?.getAsJsonObject("sectionListRenderer")
                    ?.getAsJsonArray("contents")
                Log.d(TAG, "Using sectionListRenderer fallback for search")
            }
            
            Log.d(TAG, "Search contents found: ${contents?.size() ?: 0} sections")
            
            contents?.forEach { section ->
                val sectionObj = section.asJsonObject
                val musicShelf = sectionObj.getAsJsonObject("musicShelfRenderer")
                    ?: sectionObj.getAsJsonObject("musicCardShelfRenderer")
                    ?: sectionObj.getAsJsonObject("itemSectionRenderer")
                        ?.getAsJsonArray("contents")
                        ?.firstOrNull()?.asJsonObject
                        ?.getAsJsonObject("musicShelfRenderer")
                
                musicShelf?.let { shelf ->
                    val shelfContents = shelf.getAsJsonArray("contents")
                    Log.d(TAG, "Processing shelf with ${shelfContents?.size() ?: 0} items")
                    
                    shelfContents?.forEach { item ->
                        val itemObj = item.asJsonObject
                        val musicItem = itemObj.getAsJsonObject("musicResponsiveListItemRenderer")
                            ?: itemObj.getAsJsonObject("musicTwoRowItemRenderer")
                        
                        musicItem?.let { mi ->
                            parseMusicItem(mi)?.let { parsed ->
                                when (parsed) {
                                    is SongItem -> songs.add(parsed)
                                    is ArtistItem -> artists.add(parsed)
                                    is AlbumItem -> albums.add(parsed)
                                    is PlaylistItem -> playlists.add(parsed)
                                    is VideoItem -> videos.add(parsed)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search parsing error", e)
        }
        
        return SearchResult(songs, artists, albums, playlists, videos)
    }
    
    private fun parseMusicItem(item: JsonObject): Any? {
        return try {
            if (item.has("flexColumns")) {
                parseResponsiveSongItem(item)
                    ?: parseBrowseCollectionItem(item)
            } else {
                parseTwoRowSongItem(item)
                    ?: parseBrowseCollectionItem(item)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseSearchSuggestions(response: JsonObject): List<String> {
        val suggestions = mutableListOf<String>()
        
        try {
            val contents = response.getAsJsonArray("contents")
            contents?.forEach { content ->
                val suggestionObj = content.asJsonObject
                    .getAsJsonObject("searchSuggestionsSectionRenderer")
                    ?.getAsJsonArray("contents")
                
                suggestionObj?.forEach { suggestion ->
                    val suggestionRenderer = suggestion.asJsonObject
                        .getAsJsonObject("searchSuggestionRenderer")
                    
                    suggestionRenderer?.getAsJsonObject("navigationEndpoint")
                        ?.getAsJsonObject("searchEndpoint")
                        ?.get("query")?.asString
                        ?.let { suggestions.add(it) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return suggestions
    }
    
    private fun parseHomeContent(response: JsonObject): HomeContent {
        val quickPicks = mutableListOf<SongItem>()
        val trendingSongs = mutableListOf<SongItem>()
        val newReleases = mutableListOf<AlbumItem>()
        val recommendedPlaylists = mutableListOf<PlaylistItem>()
        val moodsAndGenres = mutableListOf<MoodGenreItem>()
        
        try {
            val tabContent = response.getAsJsonObject("contents")
                ?.getAsJsonObject("singleColumnBrowseResultsRenderer")
                ?.getAsJsonArray("tabs")
                ?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("tabRenderer")
                ?.getAsJsonObject("content")

            val shelves = mutableListOf<JsonObject>()
            collectRendererObjects(tabContent, "musicCarouselShelfRenderer", shelves)
            collectRendererObjects(tabContent, "musicImmersiveCarouselShelfRenderer", shelves)
            collectRendererObjects(tabContent, "musicShelfRenderer", shelves)

            shelves.forEach { shelf ->
                parseHomeShelf(
                    shelf = shelf,
                    quickPicks = quickPicks,
                    trendingSongs = trendingSongs,
                    newReleases = newReleases,
                    recommendedPlaylists = recommendedPlaylists,
                    moodsAndGenres = moodsAndGenres
                )
            }

            if (quickPicks.isEmpty() && trendingSongs.isEmpty()) {
                val responsiveRenderers = mutableListOf<JsonObject>()
                collectRendererObjects(tabContent, "musicResponsiveListItemRenderer", responsiveRenderers)
                responsiveRenderers.forEach { renderer ->
                    parseResponsiveSongItem(renderer)?.let { song ->
                        if (quickPicks.none { it.id == song.id }) {
                            quickPicks.add(song)
                        }
                    }
                }

                val twoRowRenderers = mutableListOf<JsonObject>()
                collectRendererObjects(tabContent, "musicTwoRowItemRenderer", twoRowRenderers)
                twoRowRenderers.forEach { renderer ->
                    parseTwoRowSongItem(renderer)?.let { song ->
                        if (quickPicks.none { it.id == song.id }) {
                            quickPicks.add(song)
                        }
                    }
                }
            }

            if (moodsAndGenres.isEmpty()) {
                val navigationButtons = mutableListOf<JsonObject>()
                collectRendererObjects(tabContent, "musicNavigationButtonRenderer", navigationButtons)
                navigationButtons.forEach { renderer ->
                    parseMoodGenreItem(renderer)?.let { moodItem ->
                        addMoodGenreItem(moodItem, moodsAndGenres)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        val greeting = getGreeting()
        
        return HomeContent(
            greeting = greeting,
            quickPicks = quickPicks,
            trendingSongs = trendingSongs,
            newReleases = newReleases,
            recommendedPlaylists = recommendedPlaylists,
            moodsAndGenres = moodsAndGenres
        )
    }
    
    private fun parseHomeShelf(
        shelf: JsonObject,
        quickPicks: MutableList<SongItem>,
        trendingSongs: MutableList<SongItem>,
        newReleases: MutableList<AlbumItem>,
        recommendedPlaylists: MutableList<PlaylistItem>,
        moodsAndGenres: MutableList<MoodGenreItem>
    ) {
        val shelfTitle = extractHomeShelfTitle(shelf).lowercase()
        val isMoodShelf = shelfTitle.contains("mood") ||
            shelfTitle.contains("genre") ||
            shelfTitle.contains("feel")
        val contents = shelf.getAsJsonArray("contents")

        contents?.forEach { item ->
            if (isMoodShelf) {
                val navigationButtons = mutableListOf<JsonObject>()
                collectRendererObjects(item, "musicNavigationButtonRenderer", navigationButtons)
                navigationButtons.forEach { renderer ->
                    parseMoodGenreItem(renderer)?.let { moodItem ->
                        addMoodGenreItem(moodItem, moodsAndGenres)
                    }
                }
            }

            val responsiveItems = mutableListOf<JsonObject>()
            collectRendererObjects(item, "musicResponsiveListItemRenderer", responsiveItems)

            responsiveItems.forEach { responsive ->
                parseResponsiveSongItem(responsive)?.let { song ->
                    addHomeSong(song, shelfTitle, quickPicks, trendingSongs)
                }

                when (val parsed = parseBrowseCollectionItem(responsive)) {
                    is AlbumItem -> {
                        if (newReleases.none { it.id == parsed.id }) {
                            newReleases.add(parsed)
                        }
                    }

                    is PlaylistItem -> {
                        if (recommendedPlaylists.none { it.id == parsed.id }) {
                            recommendedPlaylists.add(parsed)
                        }
                    }
                }
            }

            val twoRowItems = mutableListOf<JsonObject>()
            collectRendererObjects(item, "musicTwoRowItemRenderer", twoRowItems)

            twoRowItems.forEach { twoRow ->
                parseTwoRowSongItem(twoRow)?.let { song ->
                    addHomeSong(song, shelfTitle, quickPicks, trendingSongs)
                }

                when (val parsed = parseBrowseCollectionItem(twoRow)) {
                    is AlbumItem -> {
                        if (newReleases.none { it.id == parsed.id }) {
                            newReleases.add(parsed)
                        }
                    }

                    is PlaylistItem -> {
                        if (recommendedPlaylists.none { it.id == parsed.id }) {
                            recommendedPlaylists.add(parsed)
                        }
                    }
                }
            }
        }
    }
    
    private fun parseArtistPage(response: JsonObject, artistId: String): ArtistPage {
        val songs = mutableListOf<SongItem>()
        val albums = mutableListOf<AlbumItem>()
        val singles = mutableListOf<AlbumItem>()
        val similarArtists = mutableListOf<ArtistItem>()
        
        var artistName = ""
        var description: String? = null
        var thumbnailUrl: String? = null
        var subscriberCount: String? = null
        
        try {
            val header = response.getAsJsonObject("header")
                ?.getAsJsonObject("musicImmersiveHeaderRenderer")
                ?: response.getAsJsonObject("header")
                    ?.getAsJsonObject("musicVisualHeaderRenderer")
            
            artistName = header?.getAsJsonObject("title")
                ?.getAsJsonArray("runs")
                ?.firstOrNull()?.asJsonObject
                ?.get("text")?.asString ?: "Unknown Artist"
            
            thumbnailUrl = header?.getAsJsonObject("thumbnail")
                ?.getAsJsonObject("musicThumbnailRenderer")
                ?.getAsJsonObject("thumbnail")
                ?.getAsJsonArray("thumbnails")
                ?.lastOrNull()?.asJsonObject
                ?.get("url")?.asString
            
            description = header?.getAsJsonObject("description")
                ?.getAsJsonArray("runs")
                ?.firstOrNull()?.asJsonObject
                ?.get("text")?.asString
            
            subscriberCount = header?.getAsJsonObject("subscriptionButton")
                ?.getAsJsonObject("subscribeButtonRenderer")
                ?.get("subscriberCountText")?.asJsonObject
                ?.getAsJsonArray("runs")
                ?.firstOrNull()?.asJsonObject
                ?.get("text")?.asString
            
            val contents = response.getAsJsonObject("contents")
                ?.getAsJsonObject("singleColumnBrowseResultsRenderer")
                ?.getAsJsonArray("tabs")
                ?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("tabRenderer")
                ?.getAsJsonObject("content")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents")
            
            contents?.forEach { section ->
                val shelf = section.asJsonObject.getAsJsonObject("musicShelfRenderer")
                    ?: section.asJsonObject.getAsJsonObject("musicCarouselShelfRenderer")
                
                shelf?.getAsJsonArray("contents")?.forEach { item ->
                    parseMusicItem(item.asJsonObject.getAsJsonObject("musicResponsiveListItemRenderer")
                        ?: item.asJsonObject.getAsJsonObject("musicTwoRowItemRenderer")
                        ?: return@forEach)?.let { parsed ->
                        when (parsed) {
                            is SongItem -> songs.add(parsed)
                            is AlbumItem -> albums.add(parsed)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return ArtistPage(
            artist = ArtistItem(artistId, artistName, thumbnailUrl, subscriberCount),
            description = description,
            thumbnailUrl = thumbnailUrl,
            songs = songs,
            albums = albums,
            singles = singles,
            similarArtists = similarArtists
        )
    }
    
    private fun parseAlbumPage(response: JsonObject, albumId: String): AlbumPage {
        val songs = mutableListOf<SongItem>()
        var albumTitle = ""
        var artistName = ""
        var artistId: String? = null
        var year: Int? = null
        var thumbnailUrl: String? = null
        var description: String? = null
        
        try {
            Log.d(TAG, "Parsing album page for: $albumId")
            
            // Try different header structures
            val header = response.getAsJsonObject("header")
                ?.getAsJsonObject("musicDetailHeaderRenderer")
                ?: response.getAsJsonObject("header")
                    ?.getAsJsonObject("musicImmersiveHeaderRenderer")
            
            albumTitle = header?.getAsJsonObject("title")
                ?.getAsJsonArray("runs")
                ?.firstOrNull()?.asJsonObject
                ?.get("text")?.asString ?: "Unknown Album"
            
            val subtitleRuns = header?.getAsJsonObject("subtitle")?.getAsJsonArray("runs")
            subtitleRuns?.forEach { run ->
                val runObj = run.asJsonObject
                val text = runObj.get("text")?.asString ?: return@forEach
                
                runObj.getAsJsonObject("navigationEndpoint")
                    ?.getAsJsonObject("browseEndpoint")?.let { browse ->
                        val pageType = browse.getAsJsonObject("browseEndpointContextSupportedConfigs")
                            ?.getAsJsonObject("browseEndpointContextMusicConfig")
                            ?.get("pageType")?.asString
                        
                        if (pageType == "MUSIC_PAGE_TYPE_ARTIST") {
                            artistName = text
                            artistId = browse.get("browseId")?.asString
                        }
                    }
                
                if (text.matches(Regex("\\d{4}"))) {
                    year = text.toIntOrNull()
                }
            }
            
            // If artist name still empty, try to get from subtitleRuns without navigation
            if (artistName.isEmpty()) {
                artistName = subtitleRuns?.filter { run ->
                    val text = run.asJsonObject?.get("text")?.asString ?: ""
                    text.isNotBlank() && !text.matches(Regex("\\d{4}")) && text != " • " && text != "Album"
                }?.firstOrNull()?.asJsonObject?.get("text")?.asString ?: "Unknown Artist"
            }
            
            // Try multiple thumbnail locations
            thumbnailUrl = header?.getAsJsonObject("thumbnail")
                ?.getAsJsonObject("croppedSquareThumbnailRenderer")
                ?.getAsJsonObject("thumbnail")
                ?.getAsJsonArray("thumbnails")
                ?.lastOrNull()?.asJsonObject
                ?.get("url")?.asString
                ?: header?.getAsJsonObject("thumbnail")
                    ?.getAsJsonObject("musicThumbnailRenderer")
                    ?.getAsJsonObject("thumbnail")
                    ?.getAsJsonArray("thumbnails")
                    ?.lastOrNull()?.asJsonObject
                    ?.get("url")?.asString
            
            description = header?.getAsJsonObject("description")
                ?.getAsJsonArray("runs")
                ?.firstOrNull()?.asJsonObject
                ?.get("text")?.asString
            
            // Try multiple content structures
            // Structure 1: singleColumnBrowseResultsRenderer with musicShelfRenderer
            var contents = response.getAsJsonObject("contents")
                ?.getAsJsonObject("singleColumnBrowseResultsRenderer")
                ?.getAsJsonArray("tabs")
                ?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("tabRenderer")
                ?.getAsJsonObject("content")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents")
                ?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("musicShelfRenderer")
                ?.getAsJsonArray("contents")
            
            // Structure 2: twoColumnBrowseResultsRenderer
            if (contents == null) {
                contents = response.getAsJsonObject("contents")
                    ?.getAsJsonObject("twoColumnBrowseResultsRenderer")
                    ?.getAsJsonObject("secondaryContents")
                    ?.getAsJsonObject("sectionListRenderer")
                    ?.getAsJsonArray("contents")
                    ?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("musicShelfRenderer")
                    ?.getAsJsonArray("contents")
                Log.d(TAG, "Using twoColumnBrowseResultsRenderer")
            }
            
            // Structure 3: singleColumnBrowseResultsRenderer with musicPlaylistShelfRenderer
            if (contents == null) {
                contents = response.getAsJsonObject("contents")
                    ?.getAsJsonObject("singleColumnBrowseResultsRenderer")
                    ?.getAsJsonArray("tabs")
                    ?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("tabRenderer")
                    ?.getAsJsonObject("content")
                    ?.getAsJsonObject("sectionListRenderer")
                    ?.getAsJsonArray("contents")
                    ?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("musicPlaylistShelfRenderer")
                    ?.getAsJsonArray("contents")
                Log.d(TAG, "Using musicPlaylistShelfRenderer")
            }
            
            Log.d(TAG, "Found ${contents?.size() ?: 0} content items")
            
            contents?.forEachIndexed { index, item ->
                val itemObj = item.asJsonObject.getAsJsonObject("musicResponsiveListItemRenderer")
                    ?: return@forEachIndexed
                
                val flexColumns = itemObj.getAsJsonArray("flexColumns")
                val songTitle = flexColumns?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.getAsJsonObject("text")
                    ?.getAsJsonArray("runs")
                    ?.firstOrNull()?.asJsonObject
                    ?.get("text")?.asString ?: return@forEachIndexed
                
                // Try multiple ways to get videoId
                var videoId = itemObj.getAsJsonObject("playlistItemData")
                    ?.get("videoId")?.asString
                
                // Fallback: Get from overlay play button
                if (videoId == null) {
                    videoId = itemObj.getAsJsonObject("overlay")
                        ?.getAsJsonObject("musicItemThumbnailOverlayRenderer")
                        ?.getAsJsonObject("content")
                        ?.getAsJsonObject("musicPlayButtonRenderer")
                        ?.getAsJsonObject("playNavigationEndpoint")
                        ?.getAsJsonObject("watchEndpoint")
                        ?.get("videoId")?.asString
                }
                
                // Fallback: Get from flexColumn navigation
                if (videoId == null) {
                    videoId = flexColumns?.firstOrNull()?.asJsonObject
                        ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.getAsJsonObject("text")
                        ?.getAsJsonArray("runs")
                        ?.firstOrNull()?.asJsonObject
                        ?.getAsJsonObject("navigationEndpoint")
                        ?.getAsJsonObject("watchEndpoint")
                        ?.get("videoId")?.asString
                }
                
                if (videoId == null) {
                    Log.w(TAG, "Could not find videoId for track: $songTitle")
                    return@forEachIndexed
                }
                
                // Get song-specific artist if available
                val songArtist = if (flexColumns != null && flexColumns.size() > 1) {
                    val artistRuns = flexColumns.get(1)?.asJsonObject
                        ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.getAsJsonObject("text")
                        ?.getAsJsonArray("runs")
                    artistRuns?.mapNotNull { it.asJsonObject?.get("text")?.asString }
                        ?.filter { it != " • " && it != " & " }
                        ?.joinToString(", ") ?: artistName
                } else artistName

                createSongItemIfEligible(
                    id = videoId,
                    title = songTitle,
                    artistsText = songArtist.ifEmpty { artistName },
                    albumId = albumId,
                    thumbnailUrl = thumbnailUrl,
                    durationMs = parseDurationMs(songArtist)
                )?.let { songs.add(it) }
            }
            
            Log.d(TAG, "Parsed ${songs.size} songs from album $albumTitle")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing album page", e)
            e.printStackTrace()
        }
        
        return AlbumPage(
            album = AlbumItem(
                id = albumId,
                title = albumTitle,
                artists = listOf(ArtistItem(artistId ?: "", artistName)),
                year = year,
                thumbnailUrl = thumbnailUrl,
                songCount = songs.size
            ),
            songs = songs,
            description = description
        )
    }
    
    private fun parsePlaylistPage(response: JsonObject, playlistId: String): PlaylistPage {
        val songs = mutableListOf<SongItem>()
        var playlistTitle = ""
        var author: String? = null
        var thumbnailUrl: String? = null
        var description: String? = null
        var continuation: String? = null
        
        try {
            val header = response.getAsJsonObject("header")
                ?.getAsJsonObject("musicDetailHeaderRenderer")
                ?: response.getAsJsonObject("header")
                    ?.getAsJsonObject("musicEditablePlaylistDetailHeaderRenderer")
                    ?.getAsJsonObject("header")
                    ?.getAsJsonObject("musicDetailHeaderRenderer")
            
                playlistTitle = readTextNode(header?.getAsJsonObject("title")) ?: "Unknown Playlist"
                author = readTextNode(header?.getAsJsonObject("subtitle"))
                thumbnailUrl = header?.let { extractThumbnailUrl(it) }
                description = readTextNode(header?.getAsJsonObject("description"))

                val playlistShelves = mutableListOf<JsonObject>()
                collectRendererObjects(response, "musicPlaylistShelfRenderer", playlistShelves)

                if (playlistShelves.isEmpty()) {
                    collectRendererObjects(response, "musicShelfRenderer", playlistShelves)
                }

                val primaryShelf = playlistShelves
                    .maxByOrNull { shelf -> shelf.getAsJsonArray("contents")?.size() ?: 0 }

                primaryShelf?.getAsJsonArray("contents")?.forEach { item ->
                    val responsiveItems = mutableListOf<JsonObject>()
                    collectRendererObjects(item, "musicResponsiveListItemRenderer", responsiveItems)

                    responsiveItems.forEach { renderer ->
                        parseResponsiveSongItem(renderer)?.let { song ->
                            if (songs.none { it.id == song.id }) {
                                songs.add(song)
                            }
                        }
                    }
                }

                if (songs.isEmpty()) {
                    val panelSongs = mutableListOf<JsonObject>()
                    collectRendererObjects(response, "playlistPanelVideoRenderer", panelSongs)
                    panelSongs.forEach { renderer ->
                        parsePlaylistPanelSongItem(renderer)?.let { song ->
                            if (songs.none { it.id == song.id }) {
                                songs.add(song)
                            }
                        }
                    }
                }

                continuation = primaryShelf?.getAsJsonArray("continuations")
                    ?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("nextContinuationData")
                    ?.get("continuation")?.asString
                    ?: response.getAsJsonObject("continuationContents")
                        ?.getAsJsonObject("musicPlaylistShelfContinuation")
                        ?.getAsJsonArray("continuations")
                        ?.firstOrNull()?.asJsonObject
                        ?.getAsJsonObject("nextContinuationData")
                        ?.get("continuation")?.asString
                
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return PlaylistPage(
            playlist = PlaylistItem(
                id = playlistId,
                title = playlistTitle,
                author = author,
                thumbnailUrl = thumbnailUrl,
                songCount = songs.size
            ),
            songs = songs,
            description = description,
            continuation = continuation
        )
    }
    
    private fun parseStreamUrl(
        response: JsonObject,
        mode: StreamMode = StreamMode.AUDIO,
        targetVideoHeight: Int = 360
    ): String? {
        return try {
            val streamingData = response.getAsJsonObject("streamingData")
            if (streamingData == null) {
                Log.w(TAG, "No streamingData in response")
                return null
            }

            val hlsManifestUrl = streamingData.get("hlsManifestUrl")?.asString
            val dashManifestUrl = streamingData.get("dashManifestUrl")?.asString
            
            val formatCandidates = mutableListOf<JsonObject>()
            streamingData.getAsJsonArray("adaptiveFormats")?.forEach { format ->
                if (format.isJsonObject) {
                    formatCandidates.add(format.asJsonObject)
                }
            }
            streamingData.getAsJsonArray("formats")?.forEach { format ->
                if (format.isJsonObject) {
                    formatCandidates.add(format.asJsonObject)
                }
            }

            if (formatCandidates.isEmpty()) {
                if (isUsableStreamUrl(hlsManifestUrl)) {
                    Log.d(TAG, "Using HLS manifest fallback")
                    return hlsManifestUrl
                }

                if (isUsableStreamUrl(dashManifestUrl)) {
                    Log.d(TAG, "Using DASH manifest fallback")
                    return dashManifestUrl
                }

                Log.w(TAG, "No adaptive formats, formats, or manifests in streamingData")
                return null
            }

            Log.d(TAG, "Found ${formatCandidates.size} stream format candidates")

            var bestAudioUrl: String? = null
            var highestBitrate = 0
            var bestVideoUrl: String? = null
            var bestVideoDistance = Int.MAX_VALUE
            var bestVideoHeight = 0
            
            formatCandidates.forEach { formatObj ->
                val mimeType = formatObj.get("mimeType")?.asString ?: return@forEach
                
                if (mode == StreamMode.VIDEO && mimeType.contains("video")) {
                    val height = formatObj.get("height")?.asInt ?: 0
                    val isMp4Video = mimeType.contains("video/mp4", ignoreCase = true)
                    var url = formatObj.get("url")?.asString

                    if (url == null) {
                        val signatureCipher = formatObj.get("signatureCipher")?.asString
                            ?: formatObj.get("cipher")?.asString
                        if (signatureCipher != null) {
                            url = parseSignatureCipher(signatureCipher)
                        }
                    }

                    if (isUsableStreamUrl(url)) {
                        val distance = kotlin.math.abs(height - targetVideoHeight)
                        // Prefer MP4 direct streams first when available for better device compatibility.
                        val scoreBoost = if (isMp4Video) -1 else 0
                        if ((distance + scoreBoost) < bestVideoDistance || ((distance + scoreBoost) == bestVideoDistance && height > bestVideoHeight)) {
                            bestVideoDistance = distance
                            bestVideoHeight = height
                            bestVideoUrl = url
                        }
                    }
                }

                if (mimeType.contains("audio")) {
                    val bitrate = formatObj.get("averageBitrate")?.asInt
                        ?: formatObj.get("bitrate")?.asInt ?: 0
                    
                    var url = formatObj.get("url")?.asString

                    if (url == null) {
                        val signatureCipher = formatObj.get("signatureCipher")?.asString
                            ?: formatObj.get("cipher")?.asString
                        if (signatureCipher != null) {
                            url = parseSignatureCipher(signatureCipher)
                        }
                    }

                    if (isUsableStreamUrl(url) && bitrate >= highestBitrate) {
                        highestBitrate = bitrate
                        bestAudioUrl = url
                        Log.d(TAG, "Found audio format: $mimeType, bitrate: $bitrate")
                    }
                }
            }

            if (mode == StreamMode.VIDEO) {
                if (isUsableStreamUrl(bestVideoUrl)) {
                    Log.d(TAG, "Selected video format around ${targetVideoHeight}p (actual=${bestVideoHeight}p)")
                    return bestVideoUrl
                }

                // Fallback to adaptive manifests only if no direct video URL was selected.
                if (isUsableStreamUrl(hlsManifestUrl)) {
                    Log.d(TAG, "Using HLS manifest fallback for video mode")
                    return hlsManifestUrl
                }

                if (isUsableStreamUrl(dashManifestUrl)) {
                    Log.d(TAG, "Using DASH manifest fallback for video mode")
                    return dashManifestUrl
                }

                Log.w(TAG, "No playable video URL found in formats")
                return null
            }
            
            if (bestAudioUrl == null) {
                if (isUsableStreamUrl(hlsManifestUrl)) {
                    Log.d(TAG, "Using HLS manifest after audio format scan")
                    return hlsManifestUrl
                }
                if (isUsableStreamUrl(dashManifestUrl)) {
                    Log.d(TAG, "Using DASH manifest after audio format scan")
                    return dashManifestUrl
                }
                Log.w(TAG, "No playable audio URL found in formats")
            }
            
            bestAudioUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing stream URL", e)
            null
        }
    }

    private data class PlayerClientConfig(
        val apiKey: String,
        val headerClientName: String,
        val clientVersion: String,
        val userAgent: String,
        val origin: String,
        val referer: String
    )

    private fun resolvePlayerClientConfig(context: JsonObject): PlayerClientConfig {
        val clientName = context.getAsJsonObject("client")
            ?.get("clientName")
            ?.asString
            ?.uppercase()
            .orEmpty()

        return when {
            clientName.contains("IOS") -> PlayerClientConfig(
                apiKey = InnerTubeApi.IOS_API_KEY,
                headerClientName = "IOS",
                clientVersion = "19.29.1",
                userAgent = "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)",
                origin = "https://music.youtube.com",
                referer = "https://music.youtube.com/"
            )
            clientName.contains("TVHTML5") -> PlayerClientConfig(
                apiKey = InnerTubeApi.TVHTML5_API_KEY,
                headerClientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                clientVersion = "2.0",
                userAgent = "Mozilla/5.0 (PlayStation 4 5.55) AppleWebKit/601.2 (KHTML, like Gecko)",
                origin = "https://www.youtube.com",
                referer = "https://www.youtube.com/"
            )
            clientName.contains("ANDROID_VR") -> PlayerClientConfig(
                apiKey = InnerTubeApi.ANDROID_MUSIC_API_KEY,
                headerClientName = "ANDROID_VR",
                clientVersion = "1.61.48",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Oculus Quest 3)",
                origin = "https://music.youtube.com",
                referer = "https://music.youtube.com/"
            )
            clientName.contains("ANDROID_MUSIC") -> PlayerClientConfig(
                apiKey = InnerTubeApi.ANDROID_MUSIC_API_KEY,
                headerClientName = "ANDROID_MUSIC",
                clientVersion = "5.01",
                userAgent = InnerTubeApi.ANDROID_USER_AGENT,
                origin = "https://music.youtube.com",
                referer = "https://music.youtube.com/"
            )
            clientName.contains("ANDROID") -> PlayerClientConfig(
                apiKey = InnerTubeApi.ANDROID_MUSIC_API_KEY,
                headerClientName = "ANDROID",
                clientVersion = "20.10.38",
                userAgent = "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip",
                origin = "https://music.youtube.com",
                referer = "https://music.youtube.com/"
            )
            clientName.contains("WEB_CREATOR") -> PlayerClientConfig(
                apiKey = InnerTubeApi.WEB_REMIX_API_KEY,
                headerClientName = "WEB_CREATOR",
                clientVersion = "1.20250312.03.01",
                userAgent = InnerTubeApi.WEB_USER_AGENT,
                origin = "https://music.youtube.com",
                referer = "https://music.youtube.com/"
            )
            clientName.contains("WEB_REMIX") || clientName.contains("WEB") -> PlayerClientConfig(
                apiKey = InnerTubeApi.WEB_REMIX_API_KEY,
                headerClientName = "WEB_REMIX",
                clientVersion = InnerTubeApi.CLIENT_VERSION,
                userAgent = InnerTubeApi.WEB_USER_AGENT,
                origin = "https://music.youtube.com",
                referer = "https://music.youtube.com/"
            )
            else -> PlayerClientConfig(
                apiKey = InnerTubeApi.ANDROID_MUSIC_API_KEY,
                headerClientName = "ANDROID_MUSIC",
                clientVersion = "5.01",
                userAgent = InnerTubeApi.ANDROID_USER_AGENT,
                origin = "https://music.youtube.com",
                referer = "https://music.youtube.com/"
            )
        }
    }

    private fun isUsableStreamUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false

        val normalized = url.trim()
        if (!(normalized.startsWith("https://") || normalized.startsWith("http://"))) {
            return false
        }

        val expire = extractExpireTimestamp(normalized)
        if (expire != null && expire <= (System.currentTimeMillis() / 1000L) + 60L) {
            return false
        }

        return true
    }

    private fun extractExpireTimestamp(url: String): Long? {
        val query = url.substringAfter('?', "")
        if (query.isBlank()) return null

        query.split('&').forEach { param ->
            val pair = param.split('=', limit = 2)
            if (pair.size != 2) return@forEach
            if (pair[0] == "expire") {
                return pair[1].toLongOrNull()
            }
        }

        return null
    }

    private fun parseSignatureCipher(signatureCipher: String): String? {
        val params = signatureCipher
            .split("&")
            .mapNotNull { part ->
                val split = part.split("=", limit = 2)
                if (split.size != 2) null else split[0] to URLDecoder.decode(split[1], "UTF-8")
            }
            .toMap()

        val url = params["url"] ?: return null
        val signature = params["sig"] ?: params["signature"]
        val signatureParam = params["sp"] ?: "signature"

        if (!signature.isNullOrBlank()) {
            return appendQueryParameter(url, signatureParam, signature)
        }

        val obfuscatedSignature = params["s"]
        if (!obfuscatedSignature.isNullOrBlank()) {
            val decipheredSignature = decipherSignature(obfuscatedSignature)
            if (!decipheredSignature.isNullOrBlank()) {
                return appendQueryParameter(url, signatureParam, decipheredSignature)
            }

            Log.w(TAG, "Cipher has obfuscated signature (s), but no decipher plan is available")
            return null
        }

        return url
    }

    private fun appendQueryParameter(url: String, key: String, value: String): String {
        val separator = if (url.contains("?")) "&" else "?"
        return "$url$separator${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
    }

    private class NewPipeOkHttpDownloader(
        private val httpClient: okhttp3.OkHttpClient
    ) : Downloader() {

        @Throws(IOException::class, ReCaptchaException::class)
        override fun execute(request: Request): Response {
            val method = request.httpMethod().uppercase()
            val headers = request.headers()

            val requestBuilder = okhttp3.Request.Builder()
                .url(request.url())

            headers?.forEach { (name, values) ->
                values.forEach { value ->
                    requestBuilder.addHeader(name, value)
                }
            }

            if (headers.isNullOrEmpty() || headers.keys.none { key -> key.equals("User-Agent", ignoreCase = true) }) {
                requestBuilder.header("User-Agent", InnerTubeApi.WEB_USER_AGENT)
            }

            val requestBody = when (method) {
                "POST", "PUT", "PATCH" -> {
                    val contentTypeHeader = headers
                        ?.entries
                        ?.firstOrNull { entry -> entry.key.equals("Content-Type", ignoreCase = true) }
                        ?.value
                        ?.firstOrNull()
                    val mediaType = contentTypeHeader?.toMediaTypeOrNull()
                    (request.dataToSend() ?: ByteArray(0)).toRequestBody(mediaType)
                }
                else -> null
            }

            requestBuilder.method(method, requestBody)

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val responseBody = if (method == "HEAD") "" else response.body?.string().orEmpty()
                if (response.code == 429 || responseBody.contains("recaptcha", ignoreCase = true)) {
                    throw ReCaptchaException("ReCaptcha challenge detected", request.url())
                }

                val responseHeaders = response.headers.names().associateWith { name ->
                    response.headers.values(name)
                }

                return Response(
                    response.code,
                    response.message.ifBlank { "" },
                    responseHeaders,
                    responseBody,
                    response.request.url.toString()
                )
            }
        }
    }
    
    private fun parseRelatedSongs(response: JsonObject): List<SongItem> {
        val songs = mutableListOf<SongItem>()
        
        try {
            val panelRenderers = mutableListOf<JsonObject>()
            collectRendererObjects(response, "playlistPanelVideoRenderer", panelRenderers)
            panelRenderers.forEach { renderer ->
                parsePlaylistPanelSongItem(renderer)?.let { song ->
                    if (songs.none { it.id == song.id }) {
                        songs.add(song)
                    }
                }
            }

            if (songs.isEmpty()) {
                val responsiveRenderers = mutableListOf<JsonObject>()
                collectRendererObjects(response, "musicResponsiveListItemRenderer", responsiveRenderers)
                responsiveRenderers.forEach { renderer ->
                    parseResponsiveSongItem(renderer)?.let { song ->
                        if (songs.none { it.id == song.id }) {
                            songs.add(song)
                        }
                    }
                }
            }

            if (songs.isEmpty()) {
                val twoRowRenderers = mutableListOf<JsonObject>()
                collectRendererObjects(response, "musicTwoRowItemRenderer", twoRowRenderers)
                twoRowRenderers.forEach { renderer ->
                    parseTwoRowSongItem(renderer)?.let { song ->
                        if (songs.none { it.id == song.id }) {
                            songs.add(song)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return songs
            .asSequence()
            .filter { song -> RecommendationContentRules.isRecommendationTrackAllowed(song) }
            .distinctBy { song -> song.id }
            .toList()
    }
    
    private fun getGreeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Good morning"
            hour < 18 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    
    // Playlist continuation
    suspend fun getPlaylistContinuation(playlistId: String, continuation: String): Result<PlaylistContinuation> = 
        withContext(Dispatchers.IO) {
            try {
                val body = JsonObject().apply {
                    addProperty("continuation", continuation)
                    add("context", createContext())
                }
                
                val response = innerTubeApi.browse(body)
                val songs = mutableListOf<SongItem>()
                var nextContinuation: String? = null
                
                // Parse continuation response
                val continuationContents = response.getAsJsonObject("continuationContents")
                    ?.getAsJsonObject("musicPlaylistShelfContinuation")
                
                continuationContents?.getAsJsonArray("contents")?.forEach { item ->
                    parseMusicItem(item.asJsonObject)?.let { parsed ->
                        if (parsed is SongItem) songs.add(parsed)
                    }
                }
                
                nextContinuation = continuationContents?.getAsJsonArray("continuations")
                    ?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("nextContinuationData")
                    ?.get("continuation")?.asString
                
                Result.success(PlaylistContinuation(songs, nextContinuation))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    // Local playlists
    fun getLocalPlaylists(): Flow<List<LocalPlaylist>> = songDao.getLocalPlaylists()
    
    suspend fun createLocalPlaylist(name: String): Long {
        return songDao.createPlaylist(
            com.beatloop.music.data.database.PlaylistEntity(
                name = name,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun saveRemotePlaylist(playlist: PlaylistItem): Result<Int> = withContext(Dispatchers.IO) {
        val playlistId = playlist.id.trim()
        if (playlistId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Invalid playlist id"))
        }

        val playlistPage = getPlaylist(playlistId).getOrElse { error ->
            return@withContext Result.failure(error)
        }

        val songsToSave = collectPlaylistSongsForSave(
            playlistId = playlistId,
            playlistPage = playlistPage
        )

        if (songsToSave.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Playlist has no songs to save"))
        }

        val targetName = playlistPage.playlist.title
            .takeIf { it.isNotBlank() }
            ?: playlist.title.takeIf { it.isNotBlank() }
            ?: "Saved Playlist"

        val existingPlaylist = getLocalPlaylists()
            .first()
            .firstOrNull { local -> local.name.equals(targetName, ignoreCase = true) }

        val targetPlaylistId = existingPlaylist?.id ?: createLocalPlaylist(targetName)
        var addedSongs = 0

        songsToSave.forEach { song ->
            if (addSongToPlaylist(targetPlaylistId, song)) {
                addedSongs += 1
            }
        }

        Result.success(addedSongs)
    }

    private suspend fun collectPlaylistSongsForSave(
        playlistId: String,
        playlistPage: PlaylistPage
    ): List<SongItem> {
        val songsById = LinkedHashMap<String, SongItem>()

        fun addSongs(songs: List<SongItem>) {
            songs.forEach { song ->
                val id = song.id.trim()
                if (id.isNotBlank() && !songsById.containsKey(id)) {
                    songsById[id] = song
                }
            }
        }

        addSongs(playlistPage.songs)

        var continuation = playlistPage.continuation
        var pageCount = 0
        while (!continuation.isNullOrBlank() && pageCount < PLAYLIST_SAVE_MAX_PAGES) {
            val continuationResult = getPlaylistContinuation(playlistId, continuation)
            continuationResult
                .onSuccess { result ->
                    addSongs(result.songs)
                    continuation = result.continuation
                }
                .onFailure { error ->
                    Log.w(
                        TAG,
                        "Failed to load playlist continuation while saving playlistId=$playlistId",
                        error
                    )
                    continuation = null
                }
            pageCount += 1
        }

        if (!continuation.isNullOrBlank()) {
            Log.w(
                TAG,
                "Playlist save hit continuation cap for playlistId=$playlistId (maxPages=$PLAYLIST_SAVE_MAX_PAGES)"
            )
        }

        return songsById.values.toList()
    }
    
    suspend fun deleteLocalPlaylist(playlistId: Long) {
        val playlist = songDao.getPlaylistEntityById(playlistId)
        if (playlist != null) {
            syncDao.upsertDeletedPlaylist(
                DeletedPlaylistSyncEntity(
                    syncId = playlist.syncId,
                    deletedAt = System.currentTimeMillis(),
                    isSynced = false
                )
            )
        }
        songDao.deletePlaylist(playlistId)
    }
    
    suspend fun renameLocalPlaylist(playlistId: Long, name: String) = 
        songDao.updatePlaylistName(playlistId, name)
    
    fun getLocalPlaylistWithSongs(playlistId: Long): Flow<LocalPlaylistWithSongs?> = 
        songDao.getPlaylistWithSongs(playlistId)
    
    suspend fun addSongToPlaylist(playlistId: Long, songId: String): Boolean {
        val added = songDao.addSongToPlaylist(playlistId, songId)
        if (added) {
            runCatching {
                recommendationRepository.recordSongInteraction(
                    songId = songId,
                    signalType = InteractionSignalTypes.PLAYLIST_ADD
                )
            }
        }
        return added
    }

    suspend fun addSongToPlaylist(playlistId: Long, song: SongItem): Boolean {
        saveSong(song)
        val added = songDao.addSongToPlaylist(playlistId, song.id)
        if (added) {
            runCatching {
                recommendationRepository.recordSongInteraction(
                    songId = song.id,
                    signalType = InteractionSignalTypes.PLAYLIST_ADD
                )
            }
        }
        return added
    }
    
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        songDao.removeSongFromPlaylist(playlistId, songId)
    }
    
    // Save song to database (required before like/playlist operations)
    suspend fun saveSong(song: SongItem) {
        val existing = songDao.getSongById(song.id)
        if (existing == null) {
            songDao.insert(
                Song(
                    id = song.id,
                    title = song.title,
                    artistsText = song.artistsText,
                    artistId = song.artistId,
                    albumId = song.albumId,
                    thumbnailUrl = song.thumbnailUrl,
                    duration = song.duration ?: 0L
                )
            )
        }
    }
    
    // Downloaded songs
    fun getDownloadedSongs(): Flow<List<SongItem>> = songDao.getDownloadedSongs()

    suspend fun getDownloadedSong(songId: String) = downloadedSongDao.getDownload(songId)

    fun getDownloadedSongSizeMap(): Flow<Map<String, Long>> =
        downloadedSongDao.getAllDownloads().map { downloads: List<DownloadedSong> ->
            downloads.associate { it.id to it.fileSize }
        }

    suspend fun setSongDownloadState(songId: String, state: DownloadState, localPath: String? = null) {
        songDao.updateDownloadState(songId, state, localPath)
    }
    
    suspend fun deleteDownload(songId: String) = withContext(Dispatchers.IO) {
        downloadedSongDao.getDownload(songId)?.let { download ->
            runCatching { File(download.filePath).delete() }
            download.thumbnailPath?.let { path ->
                runCatching { File(path).delete() }
            }
            downloadedSongDao.deleteById(songId)
        }

        songDao.updateDownloadState(songId, DownloadState.NOT_DOWNLOADED, null)
        // Legacy cleanup for previous DB writes routed via SongDao.
        songDao.deleteDownload(songId)
    }
    
    // Play history
    fun getPlayHistory(): Flow<List<SongItem>> = songDao.getPlayHistory()

    suspend fun recordPlayback(
        songId: String,
        title: String,
        artist: String,
        thumbnailUrl: String? = null
    ) {
        val now = System.currentTimeMillis()
        saveSong(
            SongItem(
                id = songId,
                title = title,
                artistsText = artist,
                thumbnailUrl = thumbnailUrl
            )
        )
        songDao.incrementPlayCount(songId, now)
        songDao.addToPlayHistory(
            com.beatloop.music.data.database.PlayHistoryEntry(
                songId = songId,
                playedAt = now
            )
        )
    }
    
    suspend fun addToPlayHistory(song: SongItem) {
        saveSong(song)
        songDao.addToPlayHistory(
            com.beatloop.music.data.database.PlayHistoryEntry(
                songId = song.id,
                playedAt = System.currentTimeMillis()
            )
        )
    }
    
    suspend fun clearPlayHistory() = songDao.clearPlayHistory()

    suspend fun recordListeningSession(
        songId: String,
        playedAt: Long,
        listenedMs: Long,
        trackDurationMs: Long?,
        previousSongId: String? = null,
        nextSongId: String? = null,
        source: String = "player"
    ) {
        if (songId.isBlank()) return

        val safeListenedMs = listenedMs.coerceAtLeast(0L)
        val safeDuration = trackDurationMs?.takeIf { it > 0L }
        val completionRate = when {
            safeDuration != null -> (safeListenedMs.toDouble() / safeDuration.toDouble()).coerceIn(0.0, 1.25).toFloat()
            safeListenedMs >= 180_000L -> 1f
            safeListenedMs > 0L -> 0.4f
            else -> 0f
        }

        val wasCompleted = when {
            safeDuration != null -> completionRate >= 0.92f
            else -> safeListenedMs >= 180_000L
        }
        val wasSkipped = when {
            safeDuration != null -> completionRate in 0.01f..0.35f
            else -> safeListenedMs in 1L..20_000L
        }

        recommendationRepository.recordListeningEvent(
            ListeningSessionEvent(
                songId = songId,
                previousSongId = previousSongId,
                nextSongId = nextSongId,
                playedAt = playedAt,
                listenedMs = safeListenedMs,
                trackDurationMs = safeDuration,
                completionRate = completionRate.coerceIn(0f, 1f),
                wasSkipped = wasSkipped,
                wasCompleted = wasCompleted,
                source = source
            )
        )
    }

    suspend fun predictNextSongs(
        seedSongId: String?,
        candidates: List<SongItem>,
        queueSongIds: Set<String>,
        limit: Int = 20
    ): List<SongItem> {
        if (candidates.isEmpty()) return emptyList()

        val recommendationCandidates = candidates
            .asSequence()
            .filter { song ->
                song.id.isNotBlank() && RecommendationContentRules.isRecommendationTrackAllowed(song)
            }
            .distinctBy { song -> song.id }
            .map { song ->
                RecommendationCandidate(
                    song = song,
                    source = RecommendationSource.RELATED,
                    sourceBoost = 6.0
                )
            }
            .toList()

        if (recommendationCandidates.isEmpty()) return emptyList()

        return recommendationRepository.rankNextSongCandidates(
            seedSongId = seedSongId,
            candidates = recommendationCandidates,
            queueSongIds = queueSongIds,
            limit = limit
        )
    }

    suspend fun recordRecommendationImpressions(
        songs: List<SongItem>,
        surface: String
    ) {
        recommendationRepository.recordRecommendationImpressions(songs, surface)
    }
    
    // Liked songs
    fun getLikedSongs(): Flow<List<SongItem>> = songDao.getLikedSongs()
    
    suspend fun likeSong(songId: String) {
        songDao.likeSong(songId)
        runCatching {
            recommendationRepository.recordSongInteraction(
                songId = songId,
                signalType = InteractionSignalTypes.LIKE
            )
        }
    }
    
    suspend fun likeSong(song: SongItem) {
        saveSong(song)
        songDao.likeSong(song.id)
        runCatching {
            recommendationRepository.recordSongInteraction(
                songId = song.id,
                signalType = InteractionSignalTypes.LIKE
            )
        }
    }
    
    suspend fun unlikeSong(songId: String) {
        songDao.unlikeSong(songId)
        runCatching {
            recommendationRepository.recordSongInteraction(
                songId = songId,
                signalType = InteractionSignalTypes.UNLIKE
            )
        }
    }
    
    suspend fun isSongLiked(songId: String): Boolean = songDao.isSongLiked(songId)
}
