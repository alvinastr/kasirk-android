package com.kasirkita.pos.domain.repository

import com.kasirkita.pos.domain.model.Shift
import kotlinx.coroutines.flow.StateFlow

interface ShiftRepository {
    val currentShift: StateFlow<Shift?>

    suspend fun getCurrentShift(): Result<Shift?>

    suspend fun openShift(
        outletId: String,
        openingCash: Long,
    ): Result<Shift>

    suspend fun closeShift(
        shiftId: String,
        closingCash: Long,
    ): Result<Shift>
}
