package com.beatloop.music.data.api

import com.google.gson.JsonObject
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface InnerTubeApi {
    companion object {
        const val WEB_REMIX_API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
        const val ANDROID_MUSIC_API_KEY = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI"
        const val IOS_API_KEY = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc"
        const val TVHTML5_API_KEY = "AIzaSyDCU8hByM-4DrUqRUYnGn-3llEO78bcxq8"
        
        // WEB_REMIX is the YouTube Music web client - more stable than ANDROID_MUSIC
        const val CLIENT_NAME = "WEB_REMIX"
        const val CLIENT_VERSION = "1.20231204.01.00"
        
        // ANDROID_MUSIC for player requests (streaming)
        const val ANDROID_CLIENT_NAME = "ANDROID_MUSIC"
        const val ANDROID_CLIENT_VERSION = "6.42.52"
        
        // User agent strings
        const val WEB_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val ANDROID_USER_AGENT = "com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 11) gzip"
    }
    
    @Headers(
        "Content-Type: application/json",
        "Origin: https://music.youtube.com",
        "Referer: https://music.youtube.com/",
        "User-Agent: $WEB_USER_AGENT"
    )
    @POST("youtubei/v1/search")
    suspend fun search(
        @Body body: JsonObject,
        @Query("key") apiKey: String = WEB_REMIX_API_KEY,
        @Query("prettyPrint") prettyPrint: Boolean = false
    ): JsonObject
    
    @Headers(
        "Content-Type: application/json",
        "Origin: https://music.youtube.com",
        "Referer: https://music.youtube.com/",
        "User-Agent: $WEB_USER_AGENT"
    )
    @POST("youtubei/v1/browse")
    suspend fun browse(
        @Body body: JsonObject,
        @Query("key") apiKey: String = WEB_REMIX_API_KEY,
        @Query("prettyPrint") prettyPrint: Boolean = false
    ): JsonObject
    
    @Headers(
        "Content-Type: application/json",
        "Origin: https://music.youtube.com",
        "Referer: https://music.youtube.com/",
        "User-Agent: $WEB_USER_AGENT"
    )
    @POST("youtubei/v1/next")
    suspend fun next(
        @Body body: JsonObject,
        @Query("key") apiKey: String = WEB_REMIX_API_KEY,
        @Query("prettyPrint") prettyPrint: Boolean = false
    ): JsonObject
    
    @Headers(
        "Content-Type: application/json",
        "X-Goog-Api-Format-Version: 1"
    )
    @POST("youtubei/v1/player")
    suspend fun player(
        @Body body: JsonObject,
        @Query("key") apiKey: String = ANDROID_MUSIC_API_KEY,
        @Query("prettyPrint") prettyPrint: Boolean = false,
        @Header("X-YouTube-Client-Name") clientName: String = "ANDROID_MUSIC",
        @Header("X-YouTube-Client-Version") clientVersion: String = "5.01",
        @Header("x-origin") origin: String = "https://music.youtube.com",
        @Header("Origin") requestOrigin: String = "https://music.youtube.com",
        @Header("Referer") referer: String = "https://music.youtube.com/",
        @Header("User-Agent") userAgent: String = ANDROID_USER_AGENT
    ): JsonObject
    
    @Headers(
        "Content-Type: application/json",
        "Origin: https://music.youtube.com",
        "Referer: https://music.youtube.com/",
        "User-Agent: $WEB_USER_AGENT"
    )
    @POST("youtubei/v1/music/get_search_suggestions")
    suspend fun getSearchSuggestions(
        @Body body: JsonObject,
        @Query("key") apiKey: String = WEB_REMIX_API_KEY,
        @Query("prettyPrint") prettyPrint: Boolean = false
    ): JsonObject
}
