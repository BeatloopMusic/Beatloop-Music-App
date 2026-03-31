package com.beatloop.music.domain.repository

import com.beatloop.music.data.model.HomeContent

/**
 * Domain-level contract for reading Home content.
 *
 * Streaming URL/piped extraction behavior remains in the underlying repository implementation.
 */
interface HomeRepository {
    suspend fun getHome(refreshNonce: Long): Result<HomeContent>
}
