package com.beatloop.music.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.beatloop.music.data.model.Album
import com.beatloop.music.data.model.Artist
import com.beatloop.music.data.model.Song

@Database(
    entities = [
        Song::class,
        Artist::class,
        Album::class,
        SearchHistory::class,
        PlayHistory::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        PlayHistoryEntry::class,
        DownloadedSong::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class BeatloopDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun downloadedSongDao(): DownloadedSongDao
}
