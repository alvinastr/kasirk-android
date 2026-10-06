package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.repository.ModifierRepository
import javax.inject.Inject

class DeleteModifierGroupUseCase @Inject constructor(
    private val modifierRepository: ModifierRepository,
) {
    suspend operator fun invoke(
        groupId: String
    ): Result<Unit> = modifierRepository.deleteModifierGroup(groupId)
}