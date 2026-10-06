package com.kasirkita.pos.data.repository

import com.kasirkita.pos.core.database.entity.CategoryEntity
import com.kasirkita.pos.core.datastore.AuthSessionDataStoreTestHelper
import com.kasirkita.pos.data.api.CategoryApi
import com.kasirkita.pos.data.local.CategoryLocalDataSource
import com.kasirkita.pos.data.model.CategoryResponse
import com.kasirkita.pos.data.model.CreateCategoryRequest
import com.kasirkita.pos.data.model.UpdateCategoryRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import retrofit2.Response

class CategoryRepositoryImplTest {

    @Test
    fun getCategories_mapsApiResponseToDomain() = runBlocking {
        val api = FakeCategoryApi(
            response = listOf(categoryResponse()),
        )
        val localDataSource = CategoryLocalDataSource(FakeCategoryDao())
        val sessionStore = AuthSessionDataStoreTestHelper.createTestStore()
        runBlocking { sessionStore.saveSession(session()) }
        val repository = CategoryRepositoryImpl(api, localDataSource, sessionStore)

        val categories = repository.getCategories().getOrThrow()

        assertEquals(1, categories.size)
        assertEquals("category-id", categories.single().id)
        assertEquals("Minuman", categories.single().name)
    }

    @Test
    fun getCategories_whenApiFails_returnsSameFailure() = runBlocking {
        val expected = IOException("network unavailable")
        val localDataSource = CategoryLocalDataSource(FakeCategoryDao())
        val sessionStore = AuthSessionDataStoreTestHelper.createTestStore()
        runBlocking { sessionStore.saveSession(session()) }
        val repository = CategoryRepositoryImpl(
            FakeCategoryApi(failure = expected),
            localDataSource,
            sessionStore,
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

        override suspend fun createCategory(request: CreateCategoryRequest): Response<CategoryResponse> =
            Response.success(categoryResponse(id = "new-category-id", name = request.name))

        override suspend fun updateCategory(id: String, request: UpdateCategoryRequest): Response<CategoryResponse> =
            Response.success(categoryResponse(id = id, name = request.name))

        override suspend fun deleteCategory(id: String): Response<Unit> =
            Response.success(Unit)
    }

    private class FakeCategoryDao : com.kasirkita.pos.core.database.dao.CategoryDao {
        private val categories = mutableListOf<CategoryEntity>()

        override suspend fun getCategories(tenantId: String): List<CategoryEntity> =
            categories.filter { it.tenantId == tenantId }

        override suspend fun insertCategories(categories: List<CategoryEntity>) {
            this.categories.removeAll { existing -> categories.any { it.id == existing.id && it.tenantId == existing.tenantId } }
            this.categories.addAll(categories)
        }

        override suspend fun insertCategory(category: CategoryEntity) {
            this.categories.removeAll { it.id == category.id && it.tenantId == category.tenantId }
            this.categories.add(category)
        }

        override suspend fun deleteCategory(category: CategoryEntity): Unit {
            this.categories.removeAll { it.id == category.id && it.tenantId == category.tenantId }
        }

        override suspend fun deleteCategory(categoryId: String): Unit {
            this.categories.removeAll { it.id == categoryId }
        }

        override suspend fun deleteAll(tenantId: String) {
            categories.removeAll { it.tenantId == tenantId }
        }

        override suspend fun replaceCategories(tenantId: String, categories: List<CategoryEntity>) {
            require(categories.all { it.tenantId == tenantId }) {
                "Cannot cache categories for a different tenant"
            }
            deleteAll(tenantId)
            insertCategories(categories)
        }
    }

    private fun session() = com.kasirkita.pos.domain.model.AuthSession(
        userId = "user-id",
        userName = "Kasir Utama",
        tenantId = "tenant-id",
        role = com.kasirkita.pos.domain.model.UserRole.CASHIER,
        outletId = "outlet-id",
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresAt = Long.MAX_VALUE,
        deviceId = "device-id",
    )

    private companion object {
        fun categoryResponse(
            id: String = "category-id",
            name: String = "Minuman",
        ) = CategoryResponse(
            id = id,
            tenantId = "tenant-id",
            name = name,
            createdAt = "2026-09-23T00:00:00.000Z",
        )
    }
}
