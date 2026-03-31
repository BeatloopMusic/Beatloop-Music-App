package com.beatloop.music.domain.usecase.search

import com.beatloop.music.data.model.SearchFilter
import com.beatloop.music.data.model.SearchResult
import com.beatloop.music.domain.repository.SearchRepository
import javax.inject.Inject

class SearchContentUseCase @Inject constructor(
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(
        query: String,
        filter: SearchFilter = SearchFilter.All
    ): Result<SearchResult> {
        return searchRepository.search(query, filter)
    }
}
