package com.beatloop.music.domain.usecase.search

import com.beatloop.music.data.database.SearchHistory
import com.beatloop.music.domain.repository.SearchRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSearchHistoryUseCase @Inject constructor(
    private val searchRepository: SearchRepository
) {
    operator fun invoke(): Flow<List<SearchHistory>> {
        return searchRepository.getSearchHistory()
    }
}
