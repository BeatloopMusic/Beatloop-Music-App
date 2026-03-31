package com.beatloop.music.di

import android.content.Context
import androidx.room.Room
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.beatloop.music.data.database.AlbumDao
import com.beatloop.music.data.database.ArtistDao
import com.beatloop.music.data.database.BeatloopDatabase
import com.beatloop.music.data.database.PlayHistoryDao
import com.beatloop.music.data.database.PlaylistDao
import com.beatloop.music.data.database.RecommendationDao
import com.beatloop.music.data.database.SearchHistoryDao
import com.beatloop.music.data.database.SongDao
import com.beatloop.music.data.database.SyncDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BeatloopDatabase {
        return Room.databaseBuilder(
            context,
            BeatloopDatabase::class.java,
            "beatloop_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    @Singleton
    fun provideSongDao(database: BeatloopDatabase): SongDao = database.songDao()
    
    @Provides
    @Singleton
    fun provideArtistDao(database: BeatloopDatabase): ArtistDao = database.artistDao()
    
    @Provides
    @Singleton
    fun provideAlbumDao(database: BeatloopDatabase): AlbumDao = database.albumDao()
    
    @Provides
    @Singleton
    fun providePlaylistDao(database: BeatloopDatabase): PlaylistDao = database.playlistDao()
    
    @Provides
    @Singleton
    fun provideSearchHistoryDao(database: BeatloopDatabase): SearchHistoryDao = database.searchHistoryDao()
    
    @Provides
    @Singleton
    fun providePlayHistoryDao(database: BeatloopDatabase): PlayHistoryDao = database.playHistoryDao()

    @Provides
    @Singleton
    fun provideRecommendationDao(database: BeatloopDatabase): RecommendationDao = database.recommendationDao()

    @Provides
    @Singleton
    fun provideSyncDao(database: BeatloopDatabase): SyncDao = database.syncDao()
    
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideDownloadedSongDao(database: BeatloopDatabase): com.beatloop.music.data.database.DownloadedSongDao = 
        database.downloadedSongDao()
}
