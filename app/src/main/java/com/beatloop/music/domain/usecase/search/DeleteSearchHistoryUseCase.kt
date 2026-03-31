package com.beatloop.music.domain.usecase.search

import com.beatloop.music.domain.repository.SearchRepository
import javax.inject.Inject

class DeleteSearchHistoryUseCase @Inject constructor(
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(query: String) {
        searchRepository.deleteSearchHistory(query)
    }
}
