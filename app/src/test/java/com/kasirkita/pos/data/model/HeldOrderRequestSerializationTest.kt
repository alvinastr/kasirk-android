package com.kasirkita.pos.data.model

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
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
}
