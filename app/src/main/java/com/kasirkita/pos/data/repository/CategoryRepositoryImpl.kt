package com.kasirkita.pos.data.repository

import com.kasirkita.pos.data.api.CategoryApi
import com.kasirkita.pos.data.model.toDomain
import com.kasirkita.pos.domain.model.Category
import com.kasirkita.pos.domain.repository.CategoryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val categoryApi: CategoryApi,
) : CategoryRepository {

    override suspend fun getCategories(): Result<List<Category>> = runCatching {
        categoryApi.getCategories().map { response -> response.toDomain() }
    }
}
