package com.kasirkita.pos.presentation.shift

import com.kasirkita.pos.domain.model.Shift
import com.kasirkita.pos.domain.model.ShiftSummary
import com.kasirkita.pos.domain.model.ShiftSummaryParty
import com.kasirkita.pos.domain.model.ShiftSummaryTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ShiftStateTest {

    @Test
    fun successfulLoad_withoutOpenShift_returnsNoShift() {
        assertSame(ShiftState.NoShift, shiftStateAfterLoad(Result.success(null)))
    }

    @Test
    fun successfulLoad_withOpenShift_returnsLoadedState() {
        val shift = openShift()

        val state = shiftStateAfterLoad(Result.success(shift))

        assertEquals(ShiftState.ShiftLoaded(shift), state)
    }

    @Test
    fun successfulSummary_returnsReviewStateWithBackendValues() {
        val shift = openShift()
        val summary = summary()

        val state = shiftStateAfterSummary(shift, Result.success(summary))

        assertEquals(ShiftState.SummaryLoaded(shift, summary), state)
    }

    @Test
    fun summaryFailure_keepsActiveShiftAndDoesNotFabricateTotals() {
        val shift = openShift()

        val state = shiftStateAfterSummary(shift, Result.failure(IOException("network unavailable")))

        assertEquals(
            ShiftState.ShiftLoaded(
                shift = shift,
                summaryError = "Tidak dapat terhubung ke server. Periksa koneksi lalu coba lagi.",
            ),
            state,
        )
    }

    @Test
    fun transportFailure_returnsReadableRetryableError() {
        val state = shiftStateAfterLoad(
            Result.failure(IOException("network unavailable")),
        )

        assertEquals(
            ShiftState.Error(
                "Tidak dapat terhubung ke server. Periksa koneksi lalu coba lagi.",
            ),
            state,
        )
    }

    @Test
    fun shiftMoney_formatsNegativeRupiahClearly() {
        assertEquals("-Rp10.000", formatShiftMoney(-10_000L))
        assertTrue(formatShiftTime("invalid timestamp").contains("invalid"))
    }

    private fun openShift() = Shift(
        id = "shift-id",
        outletId = "outlet-id",
        userId = "user-id",
        openingCash = null,
        closingCash = null,
        expectedCash = null,
        difference = null,
        status = "OPEN",
        openedAt = "2026-09-24T08:00:00.000Z",
        closedAt = null,
    )

    private fun summary() = ShiftSummary(
        shiftId = "shift-id",
        status = "OPEN",
        outlet = ShiftSummaryParty("outlet-id", "Outlet"),
        cashier = ShiftSummaryParty("user-id", "Kasir"),
        openedAt = "2026-09-24T08:00:00.000Z",
        closedAt = null,
        generatedAt = "2026-09-24T09:00:00.000Z",
        transactionCount = 1,
        totals = ShiftSummaryTotals(sales = 20_000L, cash = 10_000L, qris = 10_000L),
        products = emptyList(),
    )
}