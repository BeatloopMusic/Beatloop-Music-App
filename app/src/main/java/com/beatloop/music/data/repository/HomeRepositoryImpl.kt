package com.beatloop.music.data.repository

import com.beatloop.music.data.model.HomeContent
import com.beatloop.music.domain.repository.HomeRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepositoryImpl @Inject constructor(
    private val musicRepository: MusicRepository
) : HomeRepository {
    override suspend fun getHome(refreshNonce: Long): Result<HomeContent> {
        return musicRepository.getHome(refreshNonce)
    }
}
