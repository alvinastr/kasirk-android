package com.kasirkita.pos.presentation.transaction

import com.kasirkita.pos.domain.model.Payment
import com.kasirkita.pos.domain.model.Transaction
import com.kasirkita.pos.domain.model.TransactionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TransactionDetailStateTest {

    @Test
    fun historyWithNoTransactions_rendersEmptyState() {
        assertSame(
            TransactionHistoryState.Empty,
            transactionHistoryState(Result.success(emptyList())),
        )
    }

    @Test
    fun successfulDetail_rendersItemsPaymentTotalAndChange() {
        val transaction = transaction()

        val state = transactionDetailState(Result.success(transaction))
            as TransactionDetailState.Success

        assertEquals("product-id", state.transaction.items.single().productId)
        assertEquals("CASH", state.transaction.payments.single().method)
        assertEquals(25_000L, state.transaction.total)
        assertEquals(5_000L, state.transaction.change)
    }

    @Test
    fun failedDetail_rendersReadableRetryableError() {
        val state = transactionDetailState(
            Result.failure(IOException("network unavailable")),
        ) as TransactionDetailState.Error

        assertTrue(state.message.contains("Periksa koneksi"))
    }

    private fun transaction() = Transaction(
        id = "transaction-id",
        clientTransactionId = "client-id",
        outletId = "outlet-id",
        userId = "user-id",
        customerId = null,
        status = "COMPLETED",
        subtotal = 25_000L,
        discount = 0L,
        tax = 0L,
        total = 25_000L,
        items = listOf(
            TransactionItem(
                id = "item-id",
                productId = "product-id",
                quantity = 2,
                unitPrice = 12_500L,
                subtotal = 25_000L,
            ),
        ),
        payments = listOf(
            Payment(
                id = "payment-id",
                method = "CASH",
                status = "PAID",
                amount = 30_000L,
                paidAt = "2026-09-23T10:00:00.000Z",
            ),
        ),
        change = 5_000L,
        createdAt = "2026-09-23T10:00:00.000Z",
    )
}
