package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.repository.ModifierRepository
import javax.inject.Inject

class RemoveModifierGroupUseCase @Inject constructor(
    private val modifierRepository: ModifierRepository,
) {
    suspend operator fun invoke(
        productId: String,
        groupId: String
    ): Result<Unit> = modifierRepository.removeModifierGroup(productId, groupId)
}