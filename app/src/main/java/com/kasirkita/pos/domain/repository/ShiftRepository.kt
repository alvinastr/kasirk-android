package com.kasirkita.pos.domain.repository

import com.kasirkita.pos.domain.model.Shift
import com.kasirkita.pos.domain.model.ShiftSummary
import kotlinx.coroutines.flow.StateFlow

interface ShiftRepository {
    val currentShift: StateFlow<Shift?>

    suspend fun getCurrentShift(): Result<Shift?>

    suspend fun openShift(outletId: String): Result<Shift>

    // Retains the established presentation contract while V1 ignores reconciliation cash.
    suspend fun openShift(
        outletId: String,
        openingCash: Long,
    ): Result<Shift> = openShift(outletId)

    suspend fun closeShift(shiftId: String): Result<Shift>

    // Retains the established presentation contract while V1 ignores reconciliation cash.
    suspend fun closeShift(
        shiftId: String,
        closingCash: Long,
    ): Result<Shift> = closeShift(shiftId)

    suspend fun getShiftSummary(shiftId: String): Result<ShiftSummary>

    suspend fun clearCurrentShift(tenantId: String? = null, userId: String? = null)

    suspend fun restoreCurrentShift(expectedOutletId: String? = null)
}
