package com.beatloop.music.data.database

import androidx.room.TypeConverter
import com.beatloop.music.data.model.DownloadState

class Converters {
    @TypeConverter
    fun fromDownloadState(state: DownloadState): String = state.name
    
    @TypeConverter
    fun toDownloadState(value: String): DownloadState = 
        DownloadState.valueOf(value)

    @TypeConverter
    fun fromFloatArray(value: FloatArray?): String {
        if (value == null || value.isEmpty()) return ""
        return value.joinToString(separator = ",")
    }

    @TypeConverter
    fun toFloatArray(value: String?): FloatArray {
        if (value.isNullOrBlank()) return floatArrayOf()
        return value
            .split(',')
            .mapNotNull { token -> token.trim().toFloatOrNull() }
            .toFloatArray()
    }
}
