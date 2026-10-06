package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.ModifierGroup
import com.kasirkita.pos.domain.repository.ModifierRepository
import javax.inject.Inject

class UpdateModifierGroupUseCase @Inject constructor(
    private val modifierRepository: ModifierRepository,
) {
    suspend operator fun invoke(
        groupId: String,
        name: String? = null,
        isActive: Boolean? = null,
        displayOrder: Int? = null
    ): Result<ModifierGroup> = modifierRepository.updateModifierGroup(
        groupId = groupId,
        name = name,
        isActive = isActive,
        displayOrder = displayOrder,
    )
}