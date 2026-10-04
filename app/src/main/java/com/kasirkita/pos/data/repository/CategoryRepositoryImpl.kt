package com.kasirkita.pos.data.repository

import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.core.database.entity.CategoryEntity
import com.kasirkita.pos.core.database.entity.toDomain
import com.kasirkita.pos.data.api.CategoryApi
import com.kasirkita.pos.data.local.CategoryLocalDataSource
import com.kasirkita.pos.data.model.toDomain
import com.kasirkita.pos.domain.model.Category
import com.kasirkita.pos.domain.repository.CategoryRepository
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

    private suspend fun requireTenantId(): String = authSessionDataStore
        .getSession()
        ?.tenantId
        ?: error("Authenticated session is required")
}
