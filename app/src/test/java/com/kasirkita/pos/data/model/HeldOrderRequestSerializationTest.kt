package com.kasirkita.pos.data.model

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeldOrderRequestSerializationTest {
    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
        .create()

    @Test
    fun createRequest_doesNotSerializeSnapshotPriceFields() {
        val json = gson.toJson(
            CreateHeldOrderRequest(
                outletId = "outlet-1",
                cashierSessionId = "shift-1",
                label = "Meja 3",
                items = listOf(
                    HeldOrderItemRequest(
                        productId = "product-1",
                        quantity = 2,
                        modifierOptionIds = listOf("opt-1"),
                        note = "less sugar",
                    ),
                ),
            ),
        )

        assertTrue(json.contains("\"product_id\""))
        assertTrue(json.contains("\"quantity\""))
        assertTrue(json.contains("\"modifier_option_ids\""))
        assertFalse(json.contains("base_price"))
        assertFalse(json.contains("effective_price"))
        assertFalse(json.contains("price_delta"))
    }

    @Test
    fun updateRequest_doesNotSerializeSnapshotPriceFields() {
        val json = gson.toJson(
            UpdateHeldOrderRequest(
                expectedVersion = 4,
                label = "Take Away",
                items = listOf(
                    HeldOrderItemRequest(
                        productId = "product-1",
                        quantity = 3,
                        modifierOptionIds = listOf("opt-1", "opt-2"),
                        note = null,
                    ),
                ),
            ),
        )

        assertTrue(json.contains("\"expected_version\""))
        assertTrue(json.contains("\"product_id\""))
        assertTrue(json.contains("\"modifier_option_ids\""))
        assertFalse(json.contains("base_price"))
        assertFalse(json.contains("effective_price"))
        assertFalse(json.contains("price_delta"))
    }

    @Test
    fun checkoutRequest_serializesExactlyExpectedVersionClientTransactionIdAndPayment() {
        val json = gson.toJson(
            CheckoutHeldOrderRequest(
                expectedVersion = 4,
                clientTransactionId = "client-transaction-1",
                payment = V1PaymentRequest(method = "CASH", amountReceived = 60_000L),
            ),
        )

        val parsed = com.google.gson.JsonParser.parseString(json).asJsonObject

        assertEquals(setOf("expected_version", "client_transaction_id", "payment"), parsed.keySet())
        assertEquals(4, parsed["expected_version"].asInt)
        assertEquals("client-transaction-1", parsed["client_transaction_id"].asString)
        assertFalse(json.contains("replayed"))
        assertFalse(parsed.has("replayed"))
        assertFalse(parsed.has("transaction"))
    }

    @Test
    fun checkoutRequest_doesNotSerializeSnapshotPriceFields() {
        val json = gson.toJson(
            CheckoutHeldOrderRequest(
                expectedVersion = 1,
                clientTransactionId = "client-transaction-1",
                payment = V1PaymentRequest(method = "CASH", amountReceived = 60_000L),
            ),
        )

        assertFalse(json.contains("base_price"))
        assertFalse(json.contains("effective_price"))
        assertFalse(json.contains("price_delta"))
        assertFalse(json.contains("unit_price"))
    }

    @Test
    fun checkoutRequest_cashAndQris_parity() {
        val cash = gson.toJson(
            CheckoutHeldOrderRequest(
                expectedVersion = 1,
                clientTransactionId = "client-transaction-1",
                payment = V1PaymentRequest(method = "CASH", amountReceived = 60_000L),
            ),
        )
        val qris = gson.toJson(
            CheckoutHeldOrderRequest(
                expectedVersion = 1,
                clientTransactionId = "client-transaction-1",
                payment = V1PaymentRequest(method = "QRIS", amountReceived = null),
            ),
        )

        val cashPayment = com.google.gson.JsonParser.parseString(cash).asJsonObject["payment"].asJsonObject
        val qrisPayment = com.google.gson.JsonParser.parseString(qris).asJsonObject["payment"].asJsonObject

        assertEquals(setOf("method", "amount_received"), cashPayment.keySet())
        assertEquals(setOf("method"), qrisPayment.keySet())
        assertEquals("CASH", cashPayment["method"].asString)
        assertEquals("QRIS", qrisPayment["method"].asString)
    }

    @Test
    fun checkoutResponse_deserializesBackendEnvelopeWithoutDefaultingReplayed() {
        val json = """
            {
              "transaction": {
                "transaction_id": "transaction-1",
                "client_transaction_id": "client-transaction-1",
                "outlet_id": "outlet-1",
                "user_id": "user-1",
                "customer_id": null,
                "status": "COMPLETED",
                "subtotal": 50000,
                "discount": 0,
                "tax": 0,
                "total": 50000,
                "items": [],
                "payments": [],
                "change": null,
                "created_at": "2026-10-07T10:00:00.000Z"
              },
              "replayed": true
            }
        """.trimIndent()

        val response = gson.fromJson(json, HeldOrderCheckoutResponse::class.java)

        assertTrue(response.replayed)
        assertEquals("transaction-1", response.transaction.transactionId)
    }
}
