package com.kasirkita.pos.domain.repository

import com.kasirkita.pos.domain.model.Category

interface CategoryRepository {
    suspend fun getCategories(): Result<List<Category>>
    suspend fun createCategory(name: String): Result<Category>
    suspend fun updateCategory(id: String, name: String): Result<Category>
    suspend fun deleteCategory(id: String): Result<Unit>
}
