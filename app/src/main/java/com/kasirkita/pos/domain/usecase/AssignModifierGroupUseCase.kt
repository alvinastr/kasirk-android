package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.ProductModifierAssignment
import com.kasirkita.pos.domain.repository.ModifierRepository
import javax.inject.Inject

class AssignModifierGroupUseCase @Inject constructor(
    private val modifierRepository: ModifierRepository,
) {
    suspend operator fun invoke(
        productId: String,
        modifierGroupId: String,
        required: Boolean,
        selectionType: String,
        displayOrder: Int
    ): Result<ProductModifierAssignment> = modifierRepository.assignModifierGroup(
        productId = productId,
        modifierGroupId = modifierGroupId,
        required = required,
        selectionType = selectionType,
        displayOrder = displayOrder,
    )
}