package com.kasirkita.pos.data.repository

import com.google.gson.Gson
import com.kasirkita.pos.data.model.TransactionDetailResponse
import com.kasirkita.pos.data.model.TransactionsResponse
import com.kasirkita.pos.data.model.toDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionResponseJsonTest {

    private val gson = Gson()

    @Test
    fun listResponse_mapsEnvelopeAndSnakeCaseFields() {
        val response = gson.fromJson(
            LIST_JSON,
            TransactionsResponse::class.java,
        )
        val transaction = response.data.single().toDomain()

        assertEquals(1, response.meta.page)
        assertEquals(1, response.meta.totalPages)
        assertEquals("transaction-id", transaction.id)
        assertEquals("outlet-id", transaction.outletId)
        assertEquals(25_000L, transaction.total)
        assertTrue(transaction.items.isEmpty())
        assertTrue(transaction.payments.isEmpty())
    }

    @Test
    fun detailResponse_mapsItemsPaymentAndChange() {
        val response = gson.fromJson(
            DETAIL_JSON,
            TransactionDetailResponse::class.java,
        )
        val transaction = response.toDomain()

        assertEquals("product-id", transaction.items.single().productId)
        assertEquals(2, transaction.items.single().quantity)
        assertEquals(12_500L, transaction.items.single().unitPrice)
        assertEquals("CASH", transaction.payments.single().method)
        assertEquals(30_000L, transaction.payments.single().amount)
        assertEquals(5_000L, transaction.change)
    }

    private companion object {
        val LIST_JSON = """
            {
              "data": [{
                "transaction_id": "transaction-id",
                "client_transaction_id": "client-id",
                "outlet_id": "outlet-id",
                "user_id": "user-id",
                "customer_id": null,
                "status": "COMPLETED",
                "subtotal": 25000,
                "discount": 0,
                "tax": 0,
                "total": 25000,
                "created_at": "2026-09-23T10:00:00.000Z"
              }],
              "meta": {"page": 1, "limit": 100, "total": 1, "total_pages": 1}
            }
        """.trimIndent()

        val DETAIL_JSON = """
            {
              "transaction_id": "transaction-id",
              "client_transaction_id": "client-id",
              "outlet_id": "outlet-id",
              "user_id": "user-id",
              "customer_id": null,
              "status": "COMPLETED",
              "subtotal": 25000,
              "discount": 0,
              "tax": 0,
              "total": 25000,
              "items": [{
                "id": "item-id",
                "product_id": "product-id",
                "quantity": 2,
                "unit_price": 12500,
                "subtotal": 25000
              }],
              "payments": [{
                "id": "payment-id",
                "method": "CASH",
                "status": "PAID",
                "amount": 30000,
                "paid_at": "2026-09-23T10:00:00.000Z"
              }],
              "change": 5000,
              "created_at": "2026-09-23T10:00:00.000Z"
            }
        """.trimIndent()
    }
}
