package com.beatloop.music.data.database

import androidx.room.TypeConverter
import com.beatloop.music.data.model.DownloadState

class Converters {
    @TypeConverter
    fun fromDownloadState(state: DownloadState): String = state.name
    
    @TypeConverter
    fun toDownloadState(value: String): DownloadState = 
        DownloadState.valueOf(value)
}
