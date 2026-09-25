package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.Shift
import com.kasirkita.pos.domain.repository.ShiftRepository
import javax.inject.Inject

class OpenShiftUseCase @Inject constructor(
    private val repository: ShiftRepository,
) {
    suspend operator fun invoke(
        outletId: String,
        openingCash: Long,
    ): Result<Shift> = repository.openShift(
        outletId = outletId,
        openingCash = openingCash,
    )
}
