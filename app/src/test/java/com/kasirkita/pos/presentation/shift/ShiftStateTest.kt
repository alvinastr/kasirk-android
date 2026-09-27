package com.kasirkita.pos.presentation.shift

import com.kasirkita.pos.domain.model.Shift
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
    fun successfulClose_keepsServerReconciliationInClosedState() {
        val closed = openShift().copy(
            closingCash = 200_000L,
            expectedCash = 210_000L,
            difference = -10_000L,
            status = "CLOSED",
        )

        val state = shiftStateAfterClose(Result.success(closed))

        assertEquals(ShiftState.ShiftClosed(closed), state)
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
    fun cashValidation_requiresWholeNonNegativeAmount() {
        assertEquals(
            ShiftCashValidationResult.Invalid("Kas awal wajib diisi."),
            validateShiftCash("", "Kas awal"),
        )
        assertEquals(
            ShiftCashValidationResult.Invalid("Kas awal harus berupa angka bulat."),
            validateShiftCash("12.5", "Kas awal"),
        )
        assertEquals(
            ShiftCashValidationResult.Invalid("Kas awal tidak boleh negatif."),
            validateShiftCash("-1", "Kas awal"),
        )
        assertEquals(
            ShiftCashValidationResult.Valid(0L),
            validateShiftCash("0", "Kas awal"),
        )
    }

    @Test
    fun closedDifference_formatsNegativeRupiahClearly() {
        assertEquals("-Rp10.000", formatShiftMoney(-10_000L))
        assertTrue(formatShiftTime("invalid timestamp").contains("invalid"))
    }

    private fun openShift() = Shift(
        id = "shift-id",
        outletId = "outlet-id",
        userId = "user-id",
        openingCash = 50_000L,
        closingCash = null,
        expectedCash = null,
        difference = null,
        status = "OPEN",
        openedAt = "2026-09-24T08:00:00.000Z",
        closedAt = null,
    )
}
