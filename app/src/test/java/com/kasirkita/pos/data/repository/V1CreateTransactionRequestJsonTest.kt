package com.kasirkita.pos.data.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.kasirkita.pos.data.model.V1CreateTransactionRequest
import com.kasirkita.pos.data.model.V1CreateTransactionItemRequest
import com.kasirkita.pos.data.model.V1PaymentRequest
import com.kasirkita.pos.di.NetworkModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V1CreateTransactionRequestJsonTest {

    private val gson = NetworkModule.provideGson()

    @Test
    fun cashPayment_serializesCashTenderAsAmountReceivedNotAmount() {
        val request = V1CreateTransactionRequest(
            clientTransactionId = "c1c1c1c1-aaaa-4444-bbbb-111111111111",
            outletId = "outlet-id",
            cashierSessionId = "session-id",
            customerId = null,
            items = listOf(
                V1CreateTransactionItemRequest(
                    productId = "product-id",
                    quantity = 2,
                    modifierOptionIds = emptyList(),
                    note = null,
                ),
            ),
            payment = V1PaymentRequest(
                method = "CASH",
                amountReceived = 50_000L,
            ),
            discount = null,
        )
        val json = gson.toJsonTree(request).asJsonObject
        val paymentJson = json.getAsJsonObject("payment")

        assertTrue(paymentJson.has("amount_received"))
        assertEquals(50_000L, paymentJson.get("amount_received").asLong)
        assertFalse(paymentJson.has("amount"))
    }

    @Test
    fun qrisPayment_serializesMethodOnlyNoTender() {
        val request = V1CreateTransactionRequest(
            clientTransactionId = "c2c2c2c2-aaaa-4444-bbbb-222222222222",
            outletId = "outlet-id",
            cashierSessionId = "session-id",
            customerId = null,
            items = listOf(
                V1CreateTransactionItemRequest(
                    productId = "product-id",
                    quantity = 1,
                    modifierOptionIds = emptyList(),
                    note = null,
                ),
            ),
            payment = V1PaymentRequest(
                method = "QRIS",
                amountReceived = null,
            ),
            discount = null,
        )
        val json = Gson().toJsonTree(request).asJsonObject
        val paymentJson = json.getAsJsonObject("payment")

        assertEquals("QRIS", paymentJson.get("method").asString)
        assertFalse(paymentJson.has("amount_received"))
        assertFalse(paymentJson.has("amount"))
    }

    @Test
    fun edcPayment_serializesMethodOnlyWithoutSensitiveOrProviderFields() {
        val request = V1CreateTransactionRequest(
            clientTransactionId = "c2d2d2d2-aaaa-4444-bbbb-222222222222",
            outletId = "outlet-id",
            cashierSessionId = "session-id",
            customerId = null,
            items = listOf(
                V1CreateTransactionItemRequest(
                    productId = "product-id",
                    quantity = 1,
                    modifierOptionIds = emptyList(),
                    note = null,
                ),
            ),
            payment = V1PaymentRequest(method = "EDC", amountReceived = null),
            discount = null,
        )

        val json = gson.toJsonTree(request).asJsonObject
        val paymentJson = json.getAsJsonObject("payment")

        assertEquals(setOf("method"), paymentJson.keySet())
        assertEquals("EDC", paymentJson.get("method").asString)
        assertTrue(json.has("outlet_id"))
        assertTrue(json.has("cashier_session_id"))
        assertTrue(json.has("items"))
        assertFalse(paymentJson.has("amount"))
        assertFalse(paymentJson.has("amount_received"))
        assertFalse(paymentJson.has("change_amount"))
        assertFalse(paymentJson.has("provider"))
        assertFalse(paymentJson.has("provider_reference"))
    }

    @Test
    fun v1Request_includesExplicitCashierSessionId() {
        val request = V1CreateTransactionRequest(
            clientTransactionId = "c3c3c3c3-aaaa-4444-bbbb-333333333333",
            outletId = "outlet-id",
            cashierSessionId = "session-uuid-123",
            customerId = "customer-id",
            items = listOf(
                V1CreateTransactionItemRequest(
                    productId = "product-id",
                    quantity = 1,
                    modifierOptionIds = emptyList(),
                    note = null,
                ),
            ),
            payment = V1PaymentRequest(method = "CASH", amountReceived = 30_000L),
            discount = null,
        )
        val json = gson.toJsonTree(request).asJsonObject

        assertTrue(json.has("cashier_session_id"))
        assertEquals("session-uuid-123", json.get("cashier_session_id").asString)
    }

    @Test
    fun v1Request_allowsDuplicateProductLinesWithDifferentModifiers() {
        val request = V1CreateTransactionRequest(
            clientTransactionId = "c4c4c4c4-aaaa-4444-bbbb-444444444444",
            outletId = "outlet-id",
            cashierSessionId = "session-id",
            customerId = null,
            items = listOf(
                V1CreateTransactionItemRequest(
                    productId = "coffee-id",
                    quantity = 1,
                    modifierOptionIds = listOf("size-large-id"),
                    note = "extra hot",
                ),
                V1CreateTransactionItemRequest(
                    productId = "coffee-id",
                    quantity = 1,
                    modifierOptionIds = listOf("size-small-id"),
                    note = "cold",
                ),
            ),
            payment = V1PaymentRequest(method = "CASH", amountReceived = 60_000L),
            discount = null,
        )
        val json = gson.toJsonTree(request).asJsonObject
        val itemsArray = json.getAsJsonArray("items")

        assertEquals(2, itemsArray.size())
        assertEquals("coffee-id", itemsArray[0].asJsonObject.get("product_id").asString)
        assertEquals("coffee-id", itemsArray[1].asJsonObject.get("product_id").asString)
        assertEquals("size-large-id", itemsArray[0].asJsonObject.getAsJsonArray("modifier_option_ids")[0].asString)
        assertEquals("size-small-id", itemsArray[1].asJsonObject.getAsJsonArray("modifier_option_ids")[0].asString)
    }

    @Test
    fun cashCheckout_omitsNullOptionalFieldsAndPreservesCashTender() {
        val request = V1CreateTransactionRequest(
            clientTransactionId = "c5c5c5c5-aaaa-4444-bbbb-555555555555",
            outletId = "outlet-id",
            cashierSessionId = "session-id",
            customerId = null,
            items = listOf(
                V1CreateTransactionItemRequest(
                    productId = "product-id",
                    quantity = 1,
                    modifierOptionIds = emptyList(),
                    note = null,
                ),
            ),
            payment = V1PaymentRequest(method = "CASH", amountReceived = 20_000L),
            discount = null,
        )
        val json = gson.toJsonTree(request).asJsonObject
        val itemJson = json.getAsJsonArray("items").single().asJsonObject
        val paymentJson = json.getAsJsonObject("payment")

        assertFalse(itemJson.has("note"))
        assertFalse(json.has("discount"))
        assertFalse(json.has("customer_id"))
        assertEquals("CASH", paymentJson.get("method").asString)
        assertEquals(20_000L, paymentJson.get("amount_received").asLong)
    }

    @Test
    fun v1Request_preservesNonNullOptionalFields() {
        val request = V1CreateTransactionRequest(
            clientTransactionId = "c6c6c6c6-aaaa-4444-bbbb-666666666666",
            outletId = "outlet-id",
            cashierSessionId = "session-id",
            customerId = "customer-id",
            items = listOf(
                V1CreateTransactionItemRequest(
                    productId = "product-id",
                    quantity = 1,
                    modifierOptionIds = emptyList(),
                    note = "with note",
                ),
            ),
            payment = V1PaymentRequest(method = "CASH", amountReceived = 20_000L),
            discount = 1_000L,
        )
        val json = gson.toJsonTree(request).asJsonObject
        val itemJson = json.getAsJsonArray("items").single().asJsonObject

        assertEquals("customer-id", json.get("customer_id").asString)
        assertEquals("with note", itemJson.get("note").asString)
        assertEquals(1_000L, json.get("discount").asLong)
    }
}
