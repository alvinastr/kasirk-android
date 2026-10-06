package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.ProductModifierAssignment
import com.kasirkita.pos.domain.repository.ModifierRepository
import javax.inject.Inject

class UpdateModifierGroupAssignmentUseCase @Inject constructor(
    private val modifierRepository: ModifierRepository,
) {
    suspend operator fun invoke(
        productId: String,
        groupId: String,
        modifierGroupId: String,
        required: Boolean,
        selectionType: String,
        displayOrder: Int
    ): Result<ProductModifierAssignment> = modifierRepository.updateModifierGroupAssignment(
        productId = productId,
        groupId = groupId,
        modifierGroupId = modifierGroupId,
        required = required,
        selectionType = selectionType,
        displayOrder = displayOrder,
    )
}