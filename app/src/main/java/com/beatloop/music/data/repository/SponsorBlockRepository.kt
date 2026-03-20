package com.beatloop.music.data.repository

import com.beatloop.music.data.api.SponsorBlockApi
import com.beatloop.music.data.model.SponsorBlockSegment
import com.beatloop.music.data.model.SponsorBlockSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SponsorBlockRepository @Inject constructor(
    private val sponsorBlockApi: SponsorBlockApi
) {
    private val segmentsCache = mutableMapOf<String, List<SponsorBlockSegment>>()
    
    suspend fun getSkipSegments(
        videoId: String,
        settings: SponsorBlockSettings = SponsorBlockSettings()
    ): Result<List<SponsorBlockSegment>> = withContext(Dispatchers.IO) {
        if (!settings.enabled) {
            return@withContext Result.success(emptyList())
        }
        
        // Check cache
        segmentsCache[videoId]?.let { cachedSegments ->
            val filteredSegments = filterSegments(cachedSegments, settings)
            return@withContext Result.success(filteredSegments)
        }
        
        try {
            val categories = settings.getCategoryString()
            val segments = sponsorBlockApi.getSkipSegments(
                videoId = videoId,
                categories = "[$categories]"
            )
            
            // Cache results
            segmentsCache[videoId] = segments
            
            val filteredSegments = filterSegments(segments, settings)
            Result.success(filteredSegments)
        } catch (e: Exception) {
            // Return empty list on error (video might not have any segments)
            Result.success(emptyList())
        }
    }
    
    private fun filterSegments(
        segments: List<SponsorBlockSegment>,
        settings: SponsorBlockSettings
    ): List<SponsorBlockSegment> {
        val enabledCategories = settings.getEnabledCategories().map { it.value }
        return segments.filter { segment ->
            segment.category in enabledCategories
        }
    }
    
    fun clearCache() {
        segmentsCache.clear()
    }
    
    fun getCachedSegments(videoId: String): List<SponsorBlockSegment>? = segmentsCache[videoId]
}
