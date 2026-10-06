package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.ModifierOption
import com.kasirkita.pos.domain.repository.ModifierRepository
import javax.inject.Inject

class UpdateModifierOptionUseCase @Inject constructor(
    private val modifierRepository: ModifierRepository,
) {
    suspend operator fun invoke(
        groupId: String,
        optionId: String,
        name: String? = null,
        priceDelta: Long? = null,
        isActive: Boolean? = null,
        displayOrder: Int? = null
    ): Result<ModifierOption> = modifierRepository.updateModifierOption(
        groupId = groupId,
        optionId = optionId,
        name = name,
        priceDelta = priceDelta,
        isActive = isActive,
        displayOrder = displayOrder,
    )
}