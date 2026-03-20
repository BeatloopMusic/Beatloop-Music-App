package com.beatloop.music.data.api

import com.beatloop.music.data.model.ReturnYouTubeDislikeResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface ReturnYouTubeDislikeApi {
    @GET("votes")
    suspend fun getVotes(
        @Query("videoId") videoId: String
    ): ReturnYouTubeDislikeResponse
}
