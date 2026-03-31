package com.beatloop.music.di

import com.beatloop.music.data.repository.HomeRepositoryImpl
import com.beatloop.music.data.repository.SearchRepositoryImpl
import com.beatloop.music.domain.repository.HomeRepository
import com.beatloop.music.domain.repository.SearchRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    @Binds
    @Singleton
    abstract fun bindHomeRepository(homeRepositoryImpl: HomeRepositoryImpl): HomeRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(searchRepositoryImpl: SearchRepositoryImpl): SearchRepository
}
