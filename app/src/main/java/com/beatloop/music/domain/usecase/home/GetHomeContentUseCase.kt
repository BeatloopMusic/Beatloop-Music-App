package com.beatloop.music.domain.usecase.home

import com.beatloop.music.data.model.HomeContent
import com.beatloop.music.domain.repository.HomeRepository
import javax.inject.Inject

class GetHomeContentUseCase @Inject constructor(
    private val homeRepository: HomeRepository
) {
    suspend operator fun invoke(refreshNonce: Long = System.currentTimeMillis()): Result<HomeContent> {
        return homeRepository.getHome(refreshNonce)
    }
}
