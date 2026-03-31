package com.beatloop.music.playback

import android.content.Context

data class PlaybackResumeSnapshot(
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val positionMs: Long,
    val mode: String,
    val quality: Int,
    val playWhenReady: Boolean
)

object PlaybackResumeStore {
    private const val PREFS_NAME = "playback_resume_state"
    private const val KEY_MEDIA_ID = "media_id"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_ARTWORK = "artwork"
    private const val KEY_POSITION_MS = "position_ms"
    private const val KEY_MODE = "mode"
    private const val KEY_QUALITY = "quality"
    private const val KEY_PLAY_WHEN_READY = "play_when_ready"

    fun save(context: Context, snapshot: PlaybackResumeSnapshot) {
        prefs(context).edit()
            .putString(KEY_MEDIA_ID, snapshot.mediaId)
            .putString(KEY_TITLE, snapshot.title)
            .putString(KEY_ARTIST, snapshot.artist)
            .putString(KEY_ARTWORK, snapshot.artworkUrl)
            .putLong(KEY_POSITION_MS, snapshot.positionMs.coerceAtLeast(0L))
            .putString(KEY_MODE, snapshot.mode)
            .putInt(KEY_QUALITY, when (snapshot.quality) {
                144, 240, 360, 480, 720 -> snapshot.quality
                else -> 360
            })
            .putBoolean(KEY_PLAY_WHEN_READY, snapshot.playWhenReady)
            .apply()
    }

    fun read(context: Context): PlaybackResumeSnapshot? {
        val prefs = prefs(context)
        val mediaId = prefs.getString(KEY_MEDIA_ID, null).orEmpty()
        if (mediaId.isBlank()) return null

        val mode = prefs.getString(KEY_MODE, "song").orEmpty().ifBlank { "song" }
        return PlaybackResumeSnapshot(
            mediaId = mediaId,
            title = prefs.getString(KEY_TITLE, "Unknown").orEmpty().ifBlank { "Unknown" },
            artist = prefs.getString(KEY_ARTIST, "Unknown").orEmpty().ifBlank { "Unknown" },
            artworkUrl = prefs.getString(KEY_ARTWORK, null),
            positionMs = prefs.getLong(KEY_POSITION_MS, 0L).coerceAtLeast(0L),
            mode = if (mode == "video") "video" else "song",
            quality = when (val q = prefs.getInt(KEY_QUALITY, 360)) {
                144, 240, 360, 480, 720 -> q
                else -> 360
            },
            playWhenReady = prefs.getBoolean(KEY_PLAY_WHEN_READY, false)
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
