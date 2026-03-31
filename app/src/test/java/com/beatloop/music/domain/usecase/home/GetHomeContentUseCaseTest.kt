package com.beatloop.music.domain.usecase.home

import com.beatloop.music.data.model.HomeContent
import com.beatloop.music.domain.repository.HomeRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetHomeContentUseCaseTest {

    @Test
    fun `returns repository result with same nonce`() = runBlocking {
        val expectedNonce = 1234L
        val expected = HomeContent(greeting = "Good evening")
        val repository = FakeHomeRepository(Result.success(expected))
        val useCase = GetHomeContentUseCase(repository)

        val result = useCase(expectedNonce)

        assertEquals(expectedNonce, repository.lastNonce)
        assertTrue(result.isSuccess)
        assertEquals(expected, result.getOrNull())
    }

    @Test
    fun `returns repository failure unchanged`() = runBlocking {
        val failure = IllegalStateException("boom")
        val repository = FakeHomeRepository(Result.failure(failure))
        val useCase = GetHomeContentUseCase(repository)

        val result = useCase(99L)

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
    }

    private class FakeHomeRepository(
        private val resultToReturn: Result<HomeContent>
    ) : HomeRepository {
        var lastNonce: Long? = null

        override suspend fun getHome(refreshNonce: Long): Result<HomeContent> {
            lastNonce = refreshNonce
            return resultToReturn
        }
    }
}
