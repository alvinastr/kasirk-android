package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.ModifierGroup
import com.kasirkita.pos.domain.repository.ModifierRepository
import javax.inject.Inject

class GetModifierGroupsUseCase @Inject constructor(
    private val modifierRepository: ModifierRepository,
) {
    suspend operator fun invoke(
        includeInactive: Boolean = false
    ): Result<List<ModifierGroup>> = modifierRepository.getModifierGroups(includeInactive)
}