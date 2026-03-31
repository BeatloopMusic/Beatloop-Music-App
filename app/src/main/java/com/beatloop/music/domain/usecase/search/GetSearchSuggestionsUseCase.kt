package com.beatloop.music.domain.usecase.search

import com.beatloop.music.domain.repository.SearchRepository
import javax.inject.Inject

class GetSearchSuggestionsUseCase @Inject constructor(
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(query: String): Result<List<String>> {
        return searchRepository.getSearchSuggestions(query)
    }
}
