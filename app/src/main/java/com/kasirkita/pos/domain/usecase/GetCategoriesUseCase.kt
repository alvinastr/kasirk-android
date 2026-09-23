package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.Category
import com.kasirkita.pos.domain.repository.CategoryRepository
import javax.inject.Inject

class GetCategoriesUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
) {
    suspend operator fun invoke(): Result<List<Category>> = categoryRepository.getCategories()
}
