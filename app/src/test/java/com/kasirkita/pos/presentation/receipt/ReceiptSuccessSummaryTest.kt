package com.kasirkita.pos.presentation.receipt

import com.kasirkita.pos.domain.model.Payment
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.ReceiptCashier
import com.kasirkita.pos.domain.model.ReceiptOutlet
import com.kasirkita.pos.domain.model.ReceiptTenant
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptSuccessSummaryTest {
    @Test
    fun `maps checkout success totals payment received and change`() {
        val receipt = Receipt(
            transactionId = "tx",
            clientTransactionId = "client",
            status = "PAID",
            createdAt = "2026-01-01T00:00:00Z",
            tenant = ReceiptTenant("t", "Tenant", null),
            outlet = ReceiptOutlet("o", "Outlet", null),
            cashier = ReceiptCashier("c", "Cashier"),
            customer = null,
            items = emptyList(),
            payment = Payment("p", "CASH", "PAID", 100_000, null, 120_000, 20_000),
            subtotal = 100_000,
            discount = 0,
            tax = 0,
            total = 100_000,
            change = 20_000,
        )

        assertEquals(ReceiptSuccessSummary(100_000, "CASH", 120_000, 20_000), receipt.toSuccessSummary())
    }
}
