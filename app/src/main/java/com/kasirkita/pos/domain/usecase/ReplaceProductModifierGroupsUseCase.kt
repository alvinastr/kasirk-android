package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.ProductModifierAssignment
import com.kasirkita.pos.domain.repository.ModifierRepository
import javax.inject.Inject

class ReplaceProductModifierGroupsUseCase @Inject constructor(
    private val modifierRepository: ModifierRepository,
) {
    suspend operator fun invoke(
        productId: String,
        assignments: List<ProductModifierAssignment>,
    ): Result<List<ProductModifierAssignment>> =
        modifierRepository.replaceModifierGroups(productId, assignments)
}
