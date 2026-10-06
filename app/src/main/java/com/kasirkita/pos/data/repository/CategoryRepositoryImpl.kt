package com.kasirkita.pos.data.repository

import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.core.database.entity.CategoryEntity
import com.kasirkita.pos.core.database.entity.toDomain
import com.kasirkita.pos.data.api.CategoryApi
import com.kasirkita.pos.data.local.CategoryLocalDataSource
import com.kasirkita.pos.data.model.CategoryResponse
import com.kasirkita.pos.data.model.CreateCategoryRequest
import com.kasirkita.pos.data.model.UpdateCategoryRequest
import com.kasirkita.pos.data.model.toDomain
import com.kasirkita.pos.domain.model.Category
import com.kasirkita.pos.domain.repository.CategoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val categoryApi: CategoryApi,
    private val localDataSource: CategoryLocalDataSource,
    private val authSessionDataStore: AuthSessionDataStore,
) : CategoryRepository {

    override suspend fun getCategories(): Result<List<Category>> = runCatching {
        val tenantId = requireTenantId()
        try {
            val remoteCategories = categoryApi.getCategories()
            val entities = remoteCategories.map { response ->
                CategoryEntity(
                    id = response.id,
                    tenantId = response.tenantId,
                    name = response.name,
                )
            }
            localDataSource.saveCategories(tenantId, entities)
            remoteCategories.map { it.toDomain() }
        } catch (error: Throwable) {
            val cached = localDataSource.getCategories(tenantId)
            if (cached.isNotEmpty()) {
                cached.map { it.toDomain() }
            } else {
                throw error
            }
        }
    }

    override suspend fun createCategory(name: String): Result<Category> = runCatching {
        val tenantId = requireTenantId()
        val response = withContext(Dispatchers.IO) {
            categoryApi.createCategory(CreateCategoryRequest(name = name))
        }
        if (!response.isSuccessful) {
            throw RuntimeException("Failed to create category: ${response.code()} ${response.message()}")
        }
        val categoryResponse = response.body()!!
        val entity = CategoryEntity(
            id = categoryResponse.id,
            tenantId = categoryResponse.tenantId,
            name = categoryResponse.name,
        )
        localDataSource.saveCategory(tenantId, entity)
        categoryResponse.toDomain()
    }

    override suspend fun updateCategory(id: String, name: String): Result<Category> = runCatching {
        val tenantId = requireTenantId()
        val response = withContext(Dispatchers.IO) {
            categoryApi.updateCategory(id, UpdateCategoryRequest(name = name))
        }
        if (!response.isSuccessful) {
            throw RuntimeException("Failed to update category: ${response.code()} ${response.message()}")
        }
        val categoryResponse = response.body()!!
        val entity = CategoryEntity(
            id = categoryResponse.id,
            tenantId = categoryResponse.tenantId,
            name = categoryResponse.name,
        )
        localDataSource.saveCategory(tenantId, entity)
        categoryResponse.toDomain()
    }

    override suspend fun deleteCategory(id: String): Result<Unit> = runCatching {
        val tenantId = requireTenantId()
        val response = withContext(Dispatchers.IO) {
            categoryApi.deleteCategory(id)
        }
        if (!response.isSuccessful) {
            throw RuntimeException("Failed to delete category: ${response.code()} ${response.message()}")
        }
        localDataSource.deleteCategory(tenantId, id)
        Unit
    }

    private suspend fun requireTenantId(): String = authSessionDataStore
        .getSession()
        ?.tenantId
        ?: error("Authenticated session is required")
}
