package com.kasirkita.pos.domain.model

import org.junit.Assert.*
import org.junit.Test

class OfflineFinancialSnapshotTest {

    @Test
    fun snapshotReflectsConfiguredModifierDeltas() {
        val basePrice = 20_000L
        val modifierDelta = 5_000L
        val effectivePrice = basePrice + modifierDelta
        val quantity = 3
        val expectedSubtotal = effectivePrice * quantity
        val expectedTotal = expectedSubtotal

        val snapshot = OfflineFinancialSnapshot(
            subtotal = expectedSubtotal,
            discount = 0L,
            tax = 0L,
            total = expectedTotal,
            paymentAmount = expectedTotal + 10_000L,
        )

        assertEquals(75_000L, snapshot.subtotal)
        assertEquals(75_000L, snapshot.total)
        assertEquals(0L, snapshot.discount)
        assertEquals(0L, snapshot.tax)
        assertEquals(85_000L, snapshot.paymentAmount)
    }

    @Test
    fun snapshotForMultipleLinesDifferentModifiers() {
        val item1Price = 20_000L
        val item1Delta = 0L
        val item1Qty = 2
        val item1Subtotal = (item1Price + item1Delta) * item1Qty

        val item2Price = 25_000L
        val item2Delta = 5_000L
        val item2Qty = 1
        val item2Subtotal = (item2Price + item2Delta) * item2Qty

        val totalSubtotal = item1Subtotal + item2Subtotal
        val totalWithDiscount = totalSubtotal - 3_000L

        val snapshot = OfflineFinancialSnapshot(
            subtotal = totalSubtotal,
            discount = 3_000L,
            tax = 0L,
            total = totalWithDiscount,
            paymentAmount = totalWithDiscount,
        )

        assertEquals(70_000L, snapshot.subtotal)
        assertEquals(3_000L, snapshot.discount)
        assertEquals(67_000L, snapshot.total)
    }

    @Test
    fun snapshotWithoutModifiersAndWithoutDiscount() {
        val price = 15_000L
        val qty = 4
        val subtotal = price * qty

        val snapshot = OfflineFinancialSnapshot(
            subtotal = subtotal,
            discount = 0L,
            tax = 0L,
            total = subtotal,
            paymentAmount = subtotal + 1_000L,
        )

        assertEquals(60_000L, snapshot.subtotal)
        assertEquals(0L, snapshot.discount)
        assertEquals(60_000L, snapshot.total)
        assertEquals(61_000L, snapshot.paymentAmount)
    }
}
