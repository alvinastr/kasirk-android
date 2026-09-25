package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.Shift
import com.kasirkita.pos.domain.repository.ShiftRepository
import javax.inject.Inject

class CloseShiftUseCase @Inject constructor(
    private val repository: ShiftRepository,
) {
    suspend operator fun invoke(
        shiftId: String,
        closingCash: Long,
    ): Result<Shift> = repository.closeShift(
        shiftId = shiftId,
        closingCash = closingCash,
    )
}
