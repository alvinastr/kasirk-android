package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.Shift
import com.kasirkita.pos.domain.repository.ShiftRepository
import javax.inject.Inject

class GetCurrentShiftUseCase @Inject constructor(
    private val repository: ShiftRepository,
) {
    suspend operator fun invoke(): Result<Shift?> = repository.getCurrentShift()
}
