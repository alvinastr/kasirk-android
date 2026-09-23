package com.kasirkita.pos.domain.repository

import com.kasirkita.pos.domain.model.Category

interface CategoryRepository {
    suspend fun getCategories(): Result<List<Category>>
}
