package com.beatloop.music.data.repository

import com.beatloop.music.data.api.InnerTubeApi
import com.beatloop.music.data.api.LrcLibApi
import com.beatloop.music.data.model.Lyrics
import com.beatloop.music.data.model.parseSyncedLyrics
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepository @Inject constructor(
    private val lrcLibApi: LrcLibApi,
    private val innerTubeApi: InnerTubeApi
) {
    private val lyricsCache = mutableMapOf<String, Lyrics>()
    private val visitorData by lazy { generateVisitorData() }
    
    suspend fun getLyrics(
        songId: String,
        title: String,
        artist: String,
        album: String? = null,
        duration: Int? = null
    ): Result<Lyrics> = withContext(Dispatchers.IO) {
        // Check cache first
        lyricsCache[songId]?.let { return@withContext Result.success(it) }

        try {
            // Try exact match first
            val response = lrcLibApi.getLyrics(
                trackName = title,
                artistName = artist,
                albumName = album,
                duration = duration
            )
            
            if (response != null && (response.syncedLyrics != null || response.plainLyrics != null)) {
                val lyrics = Lyrics(
                    songId = songId,
                    plainLyrics = response.plainLyrics,
                    syncedLyrics = response.syncedLyrics?.let { parseSyncedLyrics(it) },
                    source = "LrcLib"
                )
                lyricsCache[songId] = lyrics
                return@withContext Result.success(lyrics)
            }

            // Try search if exact match fails
            val searchResults = lrcLibApi.searchLyrics(
                trackName = title,
                artistName = artist
            )

            val bestMatch = searchResults.firstOrNull { 
                it.syncedLyrics != null || it.plainLyrics != null 
            }

            if (bestMatch != null) {
                val lyrics = Lyrics(
                    songId = songId,
                    plainLyrics = bestMatch.plainLyrics,
                    syncedLyrics = bestMatch.syncedLyrics?.let { parseSyncedLyrics(it) },
                    source = "LrcLib"
                )
                lyricsCache[songId] = lyrics
                return@withContext Result.success(lyrics)
            }

            // OuterTune-like fallback: fetch lyrics tab from watch next endpoint.
            val youtubeLyrics = fetchLyricsFromYouTubeMusic(songId)
            if (!youtubeLyrics.isNullOrBlank()) {
                val lyrics = Lyrics(
                    songId = songId,
                    plainLyrics = youtubeLyrics,
                    syncedLyrics = null,
                    source = "YouTube Music"
                )
                lyricsCache[songId] = lyrics
                return@withContext Result.success(lyrics)
            }
            
            Result.failure(Exception("No lyrics found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchLyricsFromYouTubeMusic(songId: String): String? {
        return runCatching {
            val nextBody = JsonObject().apply {
                addProperty("videoId", songId)
                addProperty("isAudioOnly", true)
                add("context", createWebContext())
            }

            val nextResponse = innerTubeApi.next(nextBody)
            val tabs = nextResponse.getAsJsonObject("contents")
                ?.getAsJsonObject("singleColumnMusicWatchNextResultsRenderer")
                ?.getAsJsonObject("tabbedRenderer")
                ?.getAsJsonObject("watchNextTabbedResultsRenderer")
                ?.getAsJsonArray("tabs")

            val lyricsBrowseEndpoint = tabs
                ?.takeIf { it.size() > 1 }
                ?.get(1)
                ?.asJsonObject
                ?.getAsJsonObject("tabRenderer")
                ?.getAsJsonObject("endpoint")
                ?.getAsJsonObject("browseEndpoint")
                ?: return null

            val browseId = lyricsBrowseEndpoint.get("browseId")?.asString
            if (browseId.isNullOrBlank()) {
                return null
            }

            val browseBody = JsonObject().apply {
                addProperty("browseId", browseId)
                lyricsBrowseEndpoint.get("params")?.asString?.takeIf { it.isNotBlank() }?.let {
                    addProperty("params", it)
                }
                add("context", createWebContext())
            }

            val browseResponse = innerTubeApi.browse(browseBody)
            val lyricRuns = browseResponse.getAsJsonObject("contents")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents")
                ?.firstOrNull()
                ?.asJsonObject
                ?.getAsJsonObject("musicDescriptionShelfRenderer")
                ?.getAsJsonObject("description")
                ?.getAsJsonArray("runs")
                ?: return null

            lyricRuns
                .mapNotNull { run -> run.asJsonObject?.get("text")?.asString }
                .joinToString(separator = "")
                .trim()
                .ifBlank { null }
        }.getOrNull()
    }

    private fun createWebContext(): JsonObject {
        return JsonObject().apply {
            add("client", JsonObject().apply {
                addProperty("clientName", "WEB_REMIX")
                addProperty("clientVersion", InnerTubeApi.CLIENT_VERSION)
                addProperty("hl", "en")
                addProperty("gl", "US")
                addProperty("visitorData", visitorData)
            })
        }
    }

    private fun generateVisitorData(): String {
        val timestamp = System.currentTimeMillis() / 1000
        return "CgtBWWR3${UUID.randomUUID().toString().take(8)}${timestamp % 1000000}"
    }
    
    fun clearCache() {
        lyricsCache.clear()
    }
    
    fun getCachedLyrics(songId: String): Lyrics? = lyricsCache[songId]
}
