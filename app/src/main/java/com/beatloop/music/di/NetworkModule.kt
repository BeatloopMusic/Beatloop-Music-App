package com.beatloop.music.di

import android.util.Log
import com.beatloop.music.data.api.InnerTubeApi
import com.beatloop.music.data.api.LrcLibApi
import com.beatloop.music.data.api.ReturnYouTubeDislikeApi
import com.beatloop.music.data.api.SponsorBlockApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message ->
            Log.d("BeatloopNetwork", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    @Named("InnerTube")
    fun provideInnerTubeRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://music.youtube.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    @Named("LrcLib")
    fun provideLrcLibRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    @Named("SponsorBlock")
    fun provideSponsorBlockRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://sponsor.ajay.app/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @Named("ReturnYouTubeDislike")
    fun provideReturnYouTubeDislikeRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://returnyoutubedislikeapi.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    fun provideInnerTubeApi(@Named("InnerTube") retrofit: Retrofit): InnerTubeApi {
        return retrofit.create(InnerTubeApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideLrcLibApi(@Named("LrcLib") retrofit: Retrofit): LrcLibApi {
        return retrofit.create(LrcLibApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideSponsorBlockApi(@Named("SponsorBlock") retrofit: Retrofit): SponsorBlockApi {
        return retrofit.create(SponsorBlockApi::class.java)
    }

    @Provides
    @Singleton
    fun provideReturnYouTubeDislikeApi(
        @Named("ReturnYouTubeDislike") retrofit: Retrofit
    ): ReturnYouTubeDislikeApi {
        return retrofit.create(ReturnYouTubeDislikeApi::class.java)
    }
}
