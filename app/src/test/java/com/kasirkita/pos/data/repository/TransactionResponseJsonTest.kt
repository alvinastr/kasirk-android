package com.kasirkita.pos.data.repository

import com.google.gson.Gson
import com.kasirkita.pos.data.model.TransactionDetailResponse
import com.kasirkita.pos.data.model.TransactionsResponse
import com.kasirkita.pos.data.model.toDomain
import org.junit.Assert.assertNull
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
    fun legacyDetailResponse_preservesDisplayFieldsAndNullableSnapshots() {
        val response = gson.fromJson(
            LEGACY_DETAIL_JSON,
            TransactionDetailResponse::class.java,
        )
        val transaction = response.toDomain()
        val item = transaction.items.single()

        assertNull(transaction.cashierSessionId)
        assertEquals("Legacy coffee", item.productName)
        assertEquals("OLD", item.sku)
        assertNull(item.productNameSnapshot)
        assertNull(item.skuSnapshot)
        assertNull(item.basePriceSnapshot)
        assertNull(item.effectivePriceSnapshot)
        assertNull(item.note)
        assertTrue(item.modifierSnapshots.isEmpty())
        assertNull(transaction.payments.single().amountReceived)
        assertNull(transaction.payments.single().changeAmount)
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
        assertEquals("Kopi Susu", transaction.items.single().productNameSnapshot)
        assertEquals("KOPI", transaction.items.single().skuSnapshot)
        assertEquals(12_000L, transaction.items.single().basePriceSnapshot)
        assertEquals(12_500L, transaction.items.single().effectivePriceSnapshot)
        assertEquals("less ice", transaction.items.single().note)
        assertEquals("group", transaction.items.single().modifierSnapshots.single().groupName)
        assertEquals("option", transaction.items.single().modifierSnapshots.single().optionName)
        assertEquals("CASH", transaction.payments.single().method)
        assertEquals(30_000L, transaction.payments.single().amount)
        assertEquals(35_000L, transaction.payments.single().amountReceived)
        assertEquals(10_000L, transaction.payments.single().changeAmount)
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

        val LEGACY_DETAIL_JSON = """
            {
              "transaction_id": "legacy-tx",
              "client_transaction_id": "legacy-client",
              "outlet_id": "outlet-id",
              "user_id": "user-id",
              "customer_id": null,
              "status": "COMPLETED",
              "subtotal": 24000,
              "discount": 0,
              "tax": 0,
              "total": 24000,
              "items": [{
                "id": "legacy-item",
                "product_id": "product-id",
                "quantity": 2,
                "unit_price": 12000,
                "subtotal": 24000,
                "product_name_snapshot": null,
                "sku_snapshot": null,
                "base_price_snapshot": null,
                "effective_price_snapshot": null,
                "note_snapshot": null,
                "product_name": "Legacy coffee",
                "sku": "OLD",
                "base_price": 12000,
                "effective_price": 12000,
                "note": null,
                "modifiers": []
              }],
              "payments": [{
                "id": "legacy-payment",
                "method": "CASH",
                "status": "PAID",
                "amount": 24000,
                "amount_received": null,
                "change_amount": null,
                "paid_at": null
              }],
              "change": null,
              "created_at": "2026-09-23T10:00:00.000Z"
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
                "subtotal": 25000,
                "product_name_snapshot": "Kopi Susu",
                "sku_snapshot": "KOPI",
                "base_price_snapshot": 12000,
                "effective_price_snapshot": 12500,
                "note_snapshot": "less ice",
                "product_name": "display name",
                "sku": "display sku",
                "base_price": 11111,
                "effective_price": 12222,
                "note": "display note",
                "modifiers": [{
                  "id": "modifier-id",
                  "modifier_group_id": "group-id",
                  "modifier_option_id": "option-id",
                  "group_name_snapshot": "group",
                  "option_name_snapshot": "option",
                  "price_delta_snapshot": 500
                }]
              }],
              "payments": [{
                "id": "payment-id",
                "method": "CASH",
                "status": "PAID",
                "amount": 30000,
                "amount_received": 35000,
                "change_amount": 10000,
                "paid_at": "2026-09-23T10:00:00.000Z"
              }],
              "change": 5000,
              "created_at": "2026-09-23T10:00:00.000Z"
            }
        """.trimIndent()
    }
}
