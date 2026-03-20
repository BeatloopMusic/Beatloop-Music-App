package com.beatloop.music.controls

import android.content.Context
import android.content.SharedPreferences

object PlaybackControlStateStore {
    private const val PREFS_NAME = "playback_control_state"
    private const val KEY_MEDIA_ID = "media_id"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_IS_PLAYING = "is_playing"
    private const val KEY_IS_LIKED = "is_liked"

    data class Snapshot(
        val mediaId: String = "",
        val title: String = "Nothing playing",
        val artist: String = "Beatloop",
        val isPlaying: Boolean = false,
        val isLiked: Boolean = false
    )

    fun save(
        context: Context,
        mediaId: String,
        title: String,
        artist: String,
        isPlaying: Boolean,
        isLiked: Boolean
    ) {
        prefs(context).edit()
            .putString(KEY_MEDIA_ID, mediaId)
            .putString(KEY_TITLE, title.ifBlank { "Nothing playing" })
            .putString(KEY_ARTIST, artist.ifBlank { "Beatloop" })
            .putBoolean(KEY_IS_PLAYING, isPlaying)
            .putBoolean(KEY_IS_LIKED, isLiked)
            .apply()
    }

    fun read(context: Context): Snapshot {
        val prefs = prefs(context)
        return Snapshot(
            mediaId = prefs.getString(KEY_MEDIA_ID, "") ?: "",
            title = prefs.getString(KEY_TITLE, "Nothing playing") ?: "Nothing playing",
            artist = prefs.getString(KEY_ARTIST, "Beatloop") ?: "Beatloop",
            isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false),
            isLiked = prefs.getBoolean(KEY_IS_LIKED, false)
        )
    }

    fun notifyStateChanged(context: Context) {
        BeatloopAppWidgetProvider.updateAllWidgets(context)
        BeatloopQuickTileService.requestUpdate(context)
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
