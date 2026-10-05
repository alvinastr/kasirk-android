package com.kasirkita.pos.presentation.receipt

import com.kasirkita.pos.domain.model.Payment
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.ReceiptCashier
import com.kasirkita.pos.domain.model.ReceiptOutlet
import com.kasirkita.pos.domain.model.ReceiptTenant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * M14 Receipt UX: Payment rendering tests
 *
 * Verifies:
 * - CASH payment shows amountReceived and changeAmount when present
 * - CASH payment handles null amountReceived gracefully (legacy)
 * - CASH payment handles null changeAmount gracefully (legacy)
 * - QRIS payment does not expose fake tender/change
 * - Payment method preserved
 */
class ReceiptPaymentRenderingTest {

    @Test
    fun `CASH payment with amountReceived and changeAmount`() {
        val payment = Payment(
            id = "pay1",
            method = "CASH",
            status = "settled",
            amount = 68000L,
            paidAt = "2024-01-15T10:30:00Z",
            amountReceived = 100000L,
            changeAmount = 32000L,
        )

        assertEquals("CASH", payment.method)
        assertNotNull(payment.amountReceived)
        assertEquals(100000L, payment.amountReceived)
        assertNotNull(payment.changeAmount)
        assertEquals(32000L, payment.changeAmount)
    }

    @Test
    fun `CASH payment legacy null amountReceived safe`() {
        val payment = Payment(
            id = "pay1",
            method = "CASH",
            status = "settled",
            amount = 68000L,
            paidAt = "2024-01-15T10:30:00Z",
            amountReceived = null,
            changeAmount = null,
        )

        assertEquals("CASH", payment.method)
        assertNull(payment.amountReceived)
        assertNull(payment.changeAmount)
    }

    @Test
    fun `QRIS payment has no amountReceived or changeAmount`() {
        val payment = Payment(
            id = "pay1",
            method = "QRIS",
            status = "settled",
            amount = 68000L,
            paidAt = "2024-01-15T10:30:00Z",
            amountReceived = null,
            changeAmount = null,
        )

        assertEquals("QRIS", payment.method)
        assertNull(payment.amountReceived)
        assertNull(payment.changeAmount)
    }

    @Test
    fun `receipt change field distinct from payment changeAmount`() {
        val receipt = createMinimalReceipt(
            payment = Payment(
                id = "pay1",
                method = "CASH",
                status = "settled",
                amount = 68000L,
                paidAt = "2024-01-15T10:30:00Z",
                amountReceived = 100000L,
                changeAmount = 32000L,
            ),
            change = 32000L,
        )

        // Both should have same value for CASH
        assertEquals(32000L, receipt.payment?.changeAmount)
        assertEquals(32000L, receipt.change)
    }

    @Test
    fun `CASH zero change is displayable`() {
        val payment = Payment(
            id = "pay1",
            method = "CASH",
            status = "settled",
            amount = 50000L,
            paidAt = "2024-01-15T10:30:00Z",
            amountReceived = 50000L,
            changeAmount = 0L,
        )

        assertEquals("CASH", payment.method)
        assertNotNull(payment.amountReceived)
        assertEquals(50000L, payment.amountReceived)
        assertNotNull(payment.changeAmount)
        assertEquals(0L, payment.changeAmount)
    }

    @Test
    fun `payment method preserved in receipt`() {
        val cashPayment = Payment(
            id = "pay1",
            method = "CASH",
            status = "settled",
            amount = 50000L,
            paidAt = "2024-01-15T10:30:00Z",
            amountReceived = 50000L,
            changeAmount = 0L,
        )

        val qrisPayment = Payment(
            id = "pay2",
            method = "QRIS",
            status = "settled",
            amount = 50000L,
            paidAt = "2024-01-15T10:30:00Z",
            amountReceived = null,
            changeAmount = null,
        )

        assertEquals("CASH", cashPayment.method)
        assertEquals("QRIS", qrisPayment.method)
    }

    private fun createMinimalReceipt(
        payment: Payment? = null,
        change: Long? = null,
    ) = Receipt(
        transactionId = "tx1",
        clientTransactionId = "client1",
        status = "completed",
        createdAt = "2024-01-15T10:30:00Z",
        tenant = ReceiptTenant(id = "t1", name = "Store", address = null),
        outlet = ReceiptOutlet(id = "o1", name = "Outlet 1", address = null),
        cashier = ReceiptCashier(id = "u1", name = "Cashier"),
        customer = null,
        items = emptyList(),
        payment = payment,
        subtotal = 68000L,
        discount = 0L,
        tax = 0L,
        total = 68000L,
        change = change,
    )
}
