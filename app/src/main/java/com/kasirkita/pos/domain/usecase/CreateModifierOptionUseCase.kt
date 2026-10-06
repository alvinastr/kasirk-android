package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.ModifierOption
import com.kasirkita.pos.domain.repository.ModifierRepository
import javax.inject.Inject

class CreateModifierOptionUseCase @Inject constructor(
    private val modifierRepository: ModifierRepository,
) {
    suspend operator fun invoke(
        groupId: String,
        name: String,
        priceDelta: Long = 0,
        isActive: Boolean = true,
        displayOrder: Int = 0
    ): Result<ModifierOption> = modifierRepository.createModifierOption(
        groupId = groupId,
        name = name,
        priceDelta = priceDelta,
        isActive = isActive,
        displayOrder = displayOrder,
    )
}