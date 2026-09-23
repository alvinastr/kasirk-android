package com.kasirkita.pos.data.repository

import com.kasirkita.pos.data.api.CategoryApi
import com.kasirkita.pos.data.model.CategoryResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CategoryRepositoryImplTest {

    @Test
    fun getCategories_mapsApiResponseToDomain() = runBlocking {
        val api = FakeCategoryApi(
            response = listOf(categoryResponse()),
        )
        val repository = CategoryRepositoryImpl(api)

        val categories = repository.getCategories().getOrThrow()

        assertEquals(1, categories.size)
        assertEquals("category-id", categories.single().id)
        assertEquals("Minuman", categories.single().name)
    }

    @Test
    fun getCategories_whenApiFails_returnsSameFailure() = runBlocking {
        val expected = IOException("network unavailable")
        val repository = CategoryRepositoryImpl(
            FakeCategoryApi(failure = expected),
        )

        val result = repository.getCategories()

        assertTrue(result.isFailure)
        assertSame(expected, result.exceptionOrNull())
    }

    private class FakeCategoryApi(
        private val response: List<CategoryResponse> = emptyList(),
        private val failure: Throwable? = null,
    ) : CategoryApi {
        override suspend fun getCategories(): List<CategoryResponse> {
            failure?.let { throwable -> throw throwable }
            return response
        }
    }

    private companion object {
        fun categoryResponse() = CategoryResponse(
            id = "category-id",
            tenantId = "tenant-id",
            name = "Minuman",
            createdAt = "2026-09-23T00:00:00.000Z",
        )
    }
}
