package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.ProductModifierAssignment
import com.kasirkita.pos.domain.repository.ModifierRepository
import javax.inject.Inject

class GetProductModifierGroupsUseCase @Inject constructor(
    private val modifierRepository: ModifierRepository,
) {
    suspend operator fun invoke(
        productId: String
    ): Result<List<ProductModifierAssignment>> = modifierRepository.getProductModifierGroups(productId)
}