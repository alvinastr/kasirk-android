package com.kasirkita.pos.data.local

import com.kasirkita.pos.core.database.dao.CategoryDao
import com.kasirkita.pos.core.database.entity.CategoryEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryLocalDataSource @Inject constructor(
    private val categoryDao: CategoryDao,
) {
    suspend fun getCategories(tenantId: String): List<CategoryEntity> =
        categoryDao.getCategories(tenantId)

    suspend fun saveCategories(tenantId: String, categories: List<CategoryEntity>) {
        categoryDao.replaceCategories(tenantId, categories)
    }
}
