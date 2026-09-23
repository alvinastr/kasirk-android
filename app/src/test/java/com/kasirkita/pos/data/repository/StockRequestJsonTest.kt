package com.kasirkita.pos.data.repository

import com.google.gson.GsonBuilder
import com.kasirkita.pos.data.model.CreateStockAdjustmentRequest
import com.kasirkita.pos.domain.model.StockAdjustmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockRequestJsonTest {

    private val gson = GsonBuilder().serializeNulls().create()

    @Test
    fun adjustmentRequest_usesBackendSnakeCaseFields() {
        val request = CreateStockAdjustmentRequest(
            outletId = "outlet-id",
            productId = "product-id",
            adjustmentType = StockAdjustmentType.ADD,
            quantity = 12,
            reason = "Stok masuk",
        )

        val json = gson.toJsonTree(request).asJsonObject

        assertEquals("outlet-id", json.get("outlet_id").asString)
        assertEquals("product-id", json.get("product_id").asString)
        assertEquals("ADD", json.get("adjustment_type").asString)
        assertEquals(12, json.get("quantity").asInt)
        assertEquals("Stok masuk", json.get("reason").asString)
        assertFalse(json.has("outletId"))
        assertFalse(json.has("productId"))
        assertFalse(json.has("adjustmentType"))
    }

    @Test
    fun adjustmentRequest_withoutReason_serializesNull() {
        val request = CreateStockAdjustmentRequest(
            outletId = "outlet-id",
            productId = "product-id",
            adjustmentType = StockAdjustmentType.DEDUCT,
            quantity = 1,
            reason = null,
        )

        val json = gson.toJsonTree(request).asJsonObject

        assertTrue(json.get("reason").isJsonNull)
    }
}
