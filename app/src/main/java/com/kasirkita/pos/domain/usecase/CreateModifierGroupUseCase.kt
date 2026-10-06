package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.ModifierGroup
import com.kasirkita.pos.domain.repository.ModifierRepository
import javax.inject.Inject

class CreateModifierGroupUseCase @Inject constructor(
    private val modifierRepository: ModifierRepository,
) {
    suspend operator fun invoke(
        name: String,
        isActive: Boolean = true,
        displayOrder: Int = 0
    ): Result<ModifierGroup> = modifierRepository.createModifierGroup(
        name = name,
        isActive = isActive,
        displayOrder = displayOrder,
    )
}