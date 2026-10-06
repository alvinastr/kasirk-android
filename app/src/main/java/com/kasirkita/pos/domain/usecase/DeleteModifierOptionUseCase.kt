package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.repository.ModifierRepository
import javax.inject.Inject

class DeleteModifierOptionUseCase @Inject constructor(
    private val modifierRepository: ModifierRepository,
) {
    suspend operator fun invoke(
        groupId: String,
        optionId: String
    ): Result<Unit> = modifierRepository.deleteModifierOption(groupId, optionId)
}