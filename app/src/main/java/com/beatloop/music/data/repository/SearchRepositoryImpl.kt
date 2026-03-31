package com.beatloop.music.data.repository

import com.beatloop.music.data.database.SearchHistory
import com.beatloop.music.data.model.SearchFilter
import com.beatloop.music.data.model.SearchResult
import com.beatloop.music.domain.repository.SearchRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val musicRepository: MusicRepository
) : SearchRepository {
    override suspend fun search(query: String, filter: SearchFilter): Result<SearchResult> {
        return musicRepository.search(query, filter)
    }

    override suspend fun getSearchSuggestions(query: String): Result<List<String>> {
        return musicRepository.getSearchSuggestions(query)
    }

    override fun getSearchHistory(): Flow<List<SearchHistory>> {
        return musicRepository.getSearchHistory()
    }

    override suspend fun deleteSearchHistory(query: String) {
        musicRepository.deleteSearchHistory(query)
    }

    override suspend fun clearSearchHistory() {
        musicRepository.clearSearchHistory()
    }
}
