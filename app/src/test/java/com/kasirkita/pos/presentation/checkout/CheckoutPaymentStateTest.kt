package com.kasirkita.pos.presentation.checkout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckoutPaymentStateTest {

    // ============================================================
    // DEFAULT STATE
    // ============================================================

    @Test
    fun defaultPaymentMethod_isSafeDeterministicCash() {
        val state = CheckoutPaymentState(totalAmount = 27_000L)

        assertEquals(CheckoutPaymentMethod.CASH, state.method)
        assertFalse(state.canSubmit)
        assertNull(state.amountReceived)
    }

    // ============================================================
    // METHOD SELECTION
    // ============================================================

    @Test
    fun cashAndQrisSelection_updateMethodDeterministically() {
        val state = CheckoutPaymentState(totalAmount = 27_000L)
            .selectQris()
            .selectCash()

        assertEquals(CheckoutPaymentMethod.CASH, state.method)
        assertNull(state.amountReceived)
        assertFalse(state.canSubmit)
    }

    @Test
    fun switchingCashToQris_clearsCashTenderAndAllowsManualConfirmation() {
        val state = CheckoutPaymentState(totalAmount = 27_000L)
            .selectTender(50_000L)
            .selectQris()

        assertEquals(CheckoutPaymentMethod.QRIS, state.method)
        assertNull(state.amountReceived)
        assertEquals(0L, state.changeAmount)
        assertEquals(0L, state.shortageAmount)
        assertTrue(state.canSubmit)
        assertEquals(V1_PAYMENT_METHOD_QRIS, state.toPayment().method)
        assertNull(state.toPayment().amountReceived)
    }

    @Test
    fun switchingQrisToCash_returnsToSafeCashStateWithoutStaleTender() {
        val state = CheckoutPaymentState(totalAmount = 27_000L)
            .selectTender(50_000L)
            .selectQris()
            .selectCash()

        assertEquals(CheckoutPaymentMethod.CASH, state.method)
        assertNull(state.amountReceived)
        assertFalse(state.canSubmit)
    }

    @Test
    fun switchingCashToEdc_clearsCashTenderAndUsesMethodOnlyPayment() {
        val state = CheckoutPaymentState(totalAmount = 27_000L)
            .selectTender(50_000L)
            .selectEdc()

        assertEquals(CheckoutPaymentMethod.EDC, state.method)
        assertNull(state.amountReceived)
        assertEquals(0L, state.changeAmount)
        assertEquals(0L, state.shortageAmount)
        assertTrue(state.canSubmit)
        assertEquals(V1_PAYMENT_METHOD_EDC, state.toPayment().method)
        assertNull(state.toPayment().amountReceived)
    }

    // ============================================================
    // UANG PAS
    // ============================================================

    @Test
    fun uangPas_setsReceivedToTotalAndZeroChange() {
        val state = CheckoutPaymentState(totalAmount = 20_000L).selectExactCash()

        assertEquals(20_000L, state.amountReceived)
        assertEquals(0L, state.changeAmount)
        assertEquals(0L, state.shortageAmount)
        assertTrue(state.canSubmit)
    }

    // ============================================================
    // QUICK TENDER GENERATOR — CORE INVARIANTS
    // ============================================================

    @Test
    fun quickTenderCandidates_areCompactDeterministicAndNeverBelowTotal() {
        val first = quickTenderCandidates(27_000L)
        val second = quickTenderCandidates(27_000L)

        assertEquals(first, second)
        assertTrue(first.isNotEmpty())
        assertTrue(first.all { it >= 27_000L })
        assertEquals(first.distinct(), first)
        assertTrue(first.size <= MAX_QUICK_TENDER_CANDIDATES)
        assertTrue(50_000L in first)
        assertTrue(100_000L in first)
    }

    @Test
    fun quickTenderCandidates_doNotDuplicateExactTotalWithUangPas() {
        val candidates = quickTenderCandidates(20_000L)

        assertFalse(20_000L in candidates)
        assertTrue(candidates.all { it > 20_000L })
    }

    // ============================================================
    // QUICK TENDER GENERATOR — FULL MATRIX
    // ============================================================

    @Test
    fun quickTenderCandidates_fullMatrix_satisfiesAllInvariants() {
        val cases = listOf(
            // total -> expected included candidates (subset check)
            0L to emptyList<Long>(),
            1L to listOf(1_000L),
            9_999L to listOf(10_000L),
            10_000L to listOf(20_000L),
            18_000L to listOf(20_000L),       // must include 20k
            20_000L to listOf(50_000L),
            27_000L to listOf(50_000L),       // must include 50k
            49_999L to listOf(50_000L),
            50_000L to listOf(100_000L),
            68_000L to listOf(100_000L),      // must include 100k
            99_999L to listOf(100_000L),
            100_000L to listOf(150_000L),
            125_000L to listOf(150_000L),     // must have candidate >125k
            250_000L to listOf(300_000L),     // must have candidate >250k
            1_250_000L to listOf(1_300_000L), // large POS total
        )

        cases.forEach { (total, expectedIncluded) ->
            val candidates = quickTenderCandidates(total)

            // Core invariants for every case
            assertTrue("$total: candidate below total: $candidates", candidates.all { it >= total })
            assertTrue("$total: negative candidate: $candidates", candidates.all { it >= 0L })
            assertEquals("$total: duplicates found: $candidates", candidates.distinct(), candidates)
            assertTrue("$total: too many candidates: $candidates", candidates.size <= MAX_QUICK_TENDER_CANDIDATES)
            assertTrue("$total: exact total should not appear in candidates", total !in candidates)

            // Expected subset checks
            expectedIncluded.forEach { expected ->
                assertTrue("$total missing expected $expected from $candidates", expected in candidates)
            }
        }
    }

    @Test
    fun quickTenderCandidates_explicitRequiredExamples() {
        // 18_000 → must include 20_000
        val c18 = quickTenderCandidates(18_000L)
        assertTrue("18k must include 20k: $c18", 20_000L in c18)
        assertTrue(c18.all { it >= 18_000L })

        // 27_000 → must include 50_000
        val c27 = quickTenderCandidates(27_000L)
        assertTrue("27k must include 50k: $c27", 50_000L in c27)
        assertTrue(c27.all { it >= 27_000L })

        // 68_000 → must include 100_000
        val c68 = quickTenderCandidates(68_000L)
        assertTrue("68k must include 100k: $c68", 100_000L in c68)
        assertTrue(c68.all { it >= 68_000L })

        // 125_000 → must provide useful candidate > 125_000
        val c125 = quickTenderCandidates(125_000L)
        assertTrue("125k must have candidate >125k: $c125", c125.any { it > 125_000L })
        assertTrue(c125.all { it >= 125_000L })

        // 250_000 → must still produce useful candidates > 250_000
        val c250 = quickTenderCandidates(250_000L)
        assertTrue("250k must have candidate >=250k and not equal: $c250",
            c250.any { it >= 250_000L && it != 250_000L })
        assertTrue(c250.all { it >= 250_000L })
    }

    @Test
    fun quickTenderCandidates_deterministicAcrossMultipleCalls() {
        val totals = listOf(0L, 1L, 9_999L, 18_000L, 27_000L, 68_000L, 125_000L, 250_000L, 5_000_000L)
        totals.forEach { total ->
            val results = (1..5).map { quickTenderCandidates(total) }
            results.drop(1).forEach { r ->
                assertEquals("Non-deterministic for total=$total", results.first(), r)
            }
        }
    }

    @Test
    fun quickTenderCandidates_noOverflowForLargeTotals() {
        // Large POS totals that could overflow arithmetic
        val largeTotals = listOf(Long.MAX_VALUE / 2, Long.MAX_VALUE / 3, 999_999_999L)
        largeTotals.forEach { total ->
            val candidates = quickTenderCandidates(total)
            assertTrue("Large total $total produced negative candidate: $candidates",
                candidates.all { it >= 0L && it >= total })
            assertTrue("Large total $total has duplicates: $candidates",
                candidates == candidates.distinct())
        }
    }

    // ============================================================
    // TENDER SELECTION
    // ============================================================

    @Test
    fun selectingCandidate_updatesReceivedAndDoesNotAutoSubmit() {
        val state = CheckoutPaymentState(totalAmount = 27_000L).selectTender(50_000L)

        assertEquals(50_000L, state.amountReceived)
        assertEquals(23_000L, state.changeAmount)
        assertTrue(state.canSubmit)
    }

    // ============================================================
    // MANUAL INPUT EDGE CASES
    // ============================================================

    @Test
    fun manualInput_handlesEmptyDigitsMalformedNegativeUnderExactAndOverpayment() {
        val empty = CheckoutPaymentState(totalAmount = 20_000L).enterManualCash("")
        assertNull(empty.amountReceived)
        assertFalse(empty.canSubmit)

        val digits = CheckoutPaymentState(totalAmount = 20_000L).enterManualCash("50000")
        assertEquals(50_000L, digits.amountReceived)
        assertEquals(30_000L, digits.changeAmount)
        assertTrue(digits.canSubmit)

        val malformed = CheckoutPaymentState(totalAmount = 20_000L).enterManualCash("abc")
        assertNull(malformed.amountReceived)
        assertFalse(malformed.canSubmit)

        val negative = CheckoutPaymentState(totalAmount = 20_000L).enterManualCash("-50000")
        assertEquals("-50000", negative.manualInput)
        assertNull(negative.amountReceived)
        assertFalse(negative.canSubmit)

        val underpaid = CheckoutPaymentState(totalAmount = 27_000L).enterManualCash("20000")
        assertFalse(underpaid.canSubmit)
        assertEquals(0L, underpaid.changeAmount)
        assertEquals(7_000L, underpaid.shortageAmount)

        assertTrue(CheckoutPaymentState(totalAmount = 20_000L).enterManualCash("20000").canSubmit)
        assertTrue(CheckoutPaymentState(totalAmount = 20_000L).enterManualCash("100000").canSubmit)
    }

    @Test
    fun manualInput_withSpacesAndSpecialChars_safe() {
        val spaces = CheckoutPaymentState(totalAmount = 20_000L).enterManualCash("50 000")
        assertNull(spaces.amountReceived) // spaces are not digits

        val mixed = CheckoutPaymentState(totalAmount = 20_000L).enterManualCash("50abc000")
        assertNull(mixed.amountReceived) // contains non-digits

        val leadingZeros = CheckoutPaymentState(totalAmount = 20_000L).enterManualCash("00050000")
        assertEquals(50_000L, leadingZeros.amountReceived) // parses correctly
    }

    @Test
    fun changeIsNeverNegative_evenWithZeroTotal() {
        val zeroTotal = CheckoutPaymentState(totalAmount = 0L).selectExactCash()
        assertEquals(0L, zeroTotal.changeAmount)
        assertEquals(0L, zeroTotal.shortageAmount)
        assertTrue(zeroTotal.canSubmit)
    }

    // ============================================================
    // V1 PAYMENT CONTRACT
    // ============================================================

    @Test
    fun cashAndQrisV1Payments_haveExpectedContract() {
        val cash = CheckoutPaymentState(totalAmount = 27_000L)
            .selectTender(50_000L)
            .toPayment()
        assertEquals(V1_PAYMENT_METHOD_CASH, cash.method)
        assertEquals(50_000L, cash.amountReceived)

        val qris = CheckoutPaymentState(totalAmount = 27_000L)
            .selectTender(50_000L)
            .selectQris()
            .toPayment()
        assertEquals(V1_PAYMENT_METHOD_QRIS, qris.method)
        assertNull(qris.amountReceived)
    }

    @Test
    fun clientComputedChange_isNotTreatedAsRequestTender() {
        // Change is derived display-only; amountReceived is the actual tender
        val state = CheckoutPaymentState(totalAmount = 27_000L).selectTender(50_000L)
        val payment = state.toPayment()
        assertEquals(50_000L, payment.amountReceived) // NOT 23_000 (the change)
    }

    @Test
    fun qrisCanSubmit_withoutAnyCashTender() {
        val qris = CheckoutPaymentState(totalAmount = 27_000L).selectQris()
        assertTrue(qris.canSubmit)
        assertNull(qris.amountReceived)
        val payment = qris.toPayment()
        assertEquals(V1_PAYMENT_METHOD_QRIS, payment.method)
        assertNull(payment.amountReceived)
    }

    @Test
    fun edcCanSubmit_withoutAnyCashTender() {
        val edc = CheckoutPaymentState(totalAmount = 27_000L).selectEdc()

        assertTrue(edc.canSubmit)
        assertNull(edc.amountReceived)
        assertEquals(V1_PAYMENT_METHOD_EDC, edc.toPayment().method)
        assertNull(edc.toPayment().amountReceived)
    }

    @Test
    fun staleCashReceivedAmount_neverLeaksIntoQrisRequest() {
        val state = CheckoutPaymentState(totalAmount = 27_000L)
            .selectTender(100_000L)     // set cash tender
            .selectQris()               // switch to QRIS

        val payment = state.toPayment()
        assertEquals(V1_PAYMENT_METHOD_QRIS, payment.method)
        assertNull(payment.amountReceived) // must NOT leak 100_000
    }

    // ============================================================
    // NEGATIVE / ZERO TENDER SAFETY
    // ============================================================

    @Test
    fun selectTender_negativeAmount_rejectedAndSafe() {
        val state = CheckoutPaymentState(totalAmount = 20_000L).selectTender(-5_000L)
        assertNull(state.amountReceived)
        assertFalse(state.canSubmit)
    }

    @Test
    fun selectTender_zeroAmount_rejectedForPositiveTotal() {
        val state = CheckoutPaymentState(totalAmount = 20_000L).selectTender(0L)
        assertEquals(0L, state.amountReceived)
        assertFalse(state.canSubmit)
    }

    // ============================================================
    // UNDERPAYMENT NEVER SUBMITS
    // ============================================================

    @Test
    fun underpayment_isInvalidAndShowsShortage() {
        val state = CheckoutPaymentState(totalAmount = 27_000L).selectTender(20_000L)
        assertFalse(state.canSubmit)
        assertEquals(7_000L, state.shortageAmount)
        assertEquals(0L, state.changeAmount)
    }
}
