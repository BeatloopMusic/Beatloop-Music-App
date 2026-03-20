package com.beatloop.music.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LyricLine(
    val startTime: Long,
    val text: String,
    val endTime: Long? = null
)

@Serializable
data class Lyrics(
    val songId: String,
    val plainLyrics: String? = null,
    val syncedLyrics: List<LyricLine>? = null,
    val source: String? = null
) {
    val isSynced: Boolean
        get() = !syncedLyrics.isNullOrEmpty()
    
    // Aliases for compatibility
    val synced: Boolean
        get() = isSynced
    
    val lines: List<LyricLine>
        get() = syncedLyrics ?: emptyList()
    
    val plainText: String?
        get() = plainLyrics
}

@Serializable
data class LrcLibResponse(
    val id: Int,
    val trackName: String?,
    val artistName: String?,
    val albumName: String?,
    val duration: Double?,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String?
)

fun parseSyncedLyrics(syncedLyrics: String): List<LyricLine> {
    val lines = mutableListOf<LyricLine>()
    val timestampRegex = "\\[(\\d+):(\\d+)(?:\\.(\\d{1,3}))?]".toRegex()
    
    syncedLyrics.lines().forEach { rawLine ->
        val timestamps = timestampRegex.findAll(rawLine).toList()
        if (timestamps.isEmpty()) {
            return@forEach
        }

        val textStart = timestamps.last().range.last + 1
        val text = rawLine.substring(textStart).trim()
        if (text.isBlank()) {
            return@forEach
        }

        timestamps.forEach { match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: 0
            val seconds = match.groupValues[2].toLongOrNull() ?: 0
            val fraction = match.groupValues[3]

            val millis = when (fraction.length) {
                1 -> (fraction.toLongOrNull() ?: 0L) * 100L
                2 -> (fraction.toLongOrNull() ?: 0L) * 10L
                3 -> fraction.toLongOrNull() ?: 0L
                else -> 0L
            }

            val startTime = (minutes * 60 * 1000) + (seconds * 1000) + millis
            lines.add(LyricLine(startTime, text))
        }
    }
    
    // Calculate end times
    return lines.mapIndexed { index, lyricLine ->
        val endTime = if (index < lines.size - 1) {
            lines[index + 1].startTime
        } else {
            null
        }
        lyricLine.copy(endTime = endTime)
    }
}
