package com.beatloop.music.domain.repository

import com.beatloop.music.data.database.SearchHistory
import com.beatloop.music.data.model.SearchFilter
import com.beatloop.music.data.model.SearchResult
import kotlinx.coroutines.flow.Flow

interface SearchRepository {
    suspend fun search(query: String, filter: SearchFilter = SearchFilter.All): Result<SearchResult>

    suspend fun getSearchSuggestions(query: String): Result<List<String>>

    fun getSearchHistory(): Flow<List<SearchHistory>>

    suspend fun deleteSearchHistory(query: String)

    suspend fun clearSearchHistory()
}