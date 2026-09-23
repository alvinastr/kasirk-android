package com.kasirkita.pos.data.repository

import com.google.gson.GsonBuilder
import com.kasirkita.pos.data.model.CreateProductRequest
import com.kasirkita.pos.data.model.UpdateProductRequest
import com.kasirkita.pos.data.model.toJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductRequestJsonTest {

    private val gson = GsonBuilder().serializeNulls().create()

    @Test
    fun createProductRequest_usesBackendSnakeCaseFields() {
        val request = CreateProductRequest(
            name = "Kopi Susu",
            sku = "KOPISUSU002",
            categoryId = null,
            price = 15_000L,
            cost = 8_000L,
            minimumStock = 0,
            trackStock = false,
        )

        val json = gson.toJsonTree(request).asJsonObject

        assertEquals("Kopi Susu", json.get("name").asString)
        assertEquals("KOPISUSU002", json.get("sku").asString)
        assertTrue(json.get("category_id").isJsonNull)
        assertEquals(15_000L, json.get("price").asLong)
        assertEquals(8_000L, json.get("cost").asLong)
        assertEquals(0, json.get("minimum_stock").asInt)
        assertFalse(json.get("track_stock").asBoolean)
        assertFalse(json.has("categoryId"))
        assertFalse(json.has("minimumStock"))
        assertFalse(json.has("trackStock"))
    }

    @Test
    fun createProductRequest_selectedCategory_mapsToCategoryId() {
        val request = CreateProductRequest(
            name = "Kopi Susu",
            sku = "KOPISUSU002",
            categoryId = "category-id",
            price = 15_000L,
            cost = 8_000L,
            minimumStock = 0,
            trackStock = true,
        )

        val json = gson.toJsonTree(request).asJsonObject

        assertEquals("category-id", json.get("category_id").asString)
        assertFalse(json.has("categoryId"))
    }

    @Test
    fun updateProductRequest_usesBackendSnakeCaseFieldsAndOptionalValues() {
        val emptyRequest = UpdateProductRequest()
        assertNull(emptyRequest.name)
        assertNull(emptyRequest.trackStock)
        assertEquals(0, emptyRequest.toJsonObject().size())

        val request = UpdateProductRequest(
            sku = "KOPISUSU003",
            categoryId = null,
            categoryIdChanged = true,
            price = 16_000L,
        )

        val json = request.toJsonObject()

        assertEquals("KOPISUSU003", json.get("sku").asString)
        assertTrue(json.get("category_id").isJsonNull)
        assertEquals(16_000L, json.get("price").asLong)
        assertEquals(3, json.size())
        assertFalse(json.has("name"))
        assertFalse(json.has("cost"))
        assertFalse(json.has("minimum_stock"))
        assertFalse(json.has("track_stock"))
        assertFalse(json.has("categoryId"))
        assertFalse(json.has("minimumStock"))
        assertFalse(json.has("trackStock"))
    }
}
