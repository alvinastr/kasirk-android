package com.kasirkita.pos.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShiftResponseContractTest {

    private val gson = Gson()

    @Test
    fun openShiftPayload_deserializesAllNullableCashAndClosedFields() {
        val response = gson.fromJson(
            """
            {
              "shift_id": "shift-1", "outlet_id": "outlet-1", "user_id": "user-1",
              "opening_cash": null, "closing_cash": null, "expected_cash": null,
              "difference": null, "status": "OPEN", "opened_at": "2026-09-24T08:00:00.000Z",
              "closed_at": null, "reconciliation_mode": "NONE"
            }
            """.trimIndent(),
            ShiftResponse::class.java,
        )

        val shift = response.toDomain()

        assertEquals("shift-1", shift.id)
        assertEquals("outlet-1", shift.outletId)
        assertEquals("OPEN", shift.status)
        assertNull(shift.openingCash)
        assertNull(shift.closingCash)
        assertNull(shift.expectedCash)
        assertNull(shift.difference)
        assertNull(shift.closedAt)
    }

    @Test
    fun shiftSummaryPayload_deserializesExactBackendFieldsAndProductOrdering() {
        val response = gson.fromJson(
            """
            {
              "shift_id": "shift-1", "status": "CLOSED",
              "outlet": { "id": "outlet-1", "name": "Outlet Utama" },
              "cashier": { "id": "user-1", "name": "Kasir Utama" },
              "opened_at": "2026-09-24T08:00:00.000Z",
              "closed_at": "2026-09-24T16:00:00.000Z",
              "generated_at": "2026-09-24T16:01:00.000Z",
              "transaction_count": 2,
              "totals": { "sales": 30000, "cash": 10000, "qris": 20000 },
              "products": [
                { "product_id": "product-2", "product_name": "Teh", "quantity": 1 },
                { "product_id": "product-1", "product_name": "Kopi", "quantity": 3 }
              ]
            }
            """.trimIndent(),
            ShiftSummaryResponse::class.java,
        )

        val summary = response.toDomain()

        assertEquals("shift-1", summary.shiftId)
        assertEquals("CLOSED", summary.status)
        assertEquals("Outlet Utama", summary.outlet.name)
        assertEquals("Kasir Utama", summary.cashier.name)
        assertEquals("2026-09-24T16:01:00.000Z", summary.generatedAt)
        assertEquals(2, summary.transactionCount)
        assertEquals(30_000L, summary.totals.sales)
        assertEquals(10_000L, summary.totals.cash)
        assertEquals(20_000L, summary.totals.qris)
        assertEquals(listOf("product-2", "product-1"), summary.products.map { it.productId })
        assertEquals(listOf(1, 3), summary.products.map { it.quantity })
    }
}
