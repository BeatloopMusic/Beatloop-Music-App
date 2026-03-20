package com.beatloop.music.data.model

import com.google.gson.annotations.SerializedName

data class VideoVotes(
    val videoId: String,
    val likes: Long,
    val dislikes: Long,
    val viewCount: Long,
    val rating: Double?
)

data class ReturnYouTubeDislikeResponse(
    @SerializedName("id")
    val id: String,
    @SerializedName("likes")
    val likes: Long? = null,
    @SerializedName("dislikes")
    val dislikes: Long? = null,
    @SerializedName("viewCount")
    val viewCount: Long? = null,
    @SerializedName("rating")
    val rating: Double? = null,
    @SerializedName("deleted")
    val deleted: Boolean? = null
)
