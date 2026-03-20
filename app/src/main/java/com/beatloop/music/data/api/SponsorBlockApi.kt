package com.beatloop.music.data.api

import com.beatloop.music.data.model.SponsorBlockSegment
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SponsorBlockApi {
    @GET("api/skipSegments")
    suspend fun getSkipSegments(
        @Query("videoID") videoId: String,
        @Query("categories") categories: String = "[\"sponsor\",\"selfpromo\",\"intro\",\"outro\",\"interaction\",\"preview\",\"music_offtopic\",\"filler\"]"
    ): List<SponsorBlockSegment>
    
    @GET("api/skipSegments/{hashPrefix}")
    suspend fun getSkipSegmentsByHash(
        @Path("hashPrefix") hashPrefix: String,
        @Query("categories") categories: String = "[\"sponsor\",\"selfpromo\",\"intro\",\"outro\"]"
    ): Map<String, List<SponsorBlockSegment>>
}
