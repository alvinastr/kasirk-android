package com.kasirkita.pos.data.repository

import com.google.gson.GsonBuilder
import com.kasirkita.pos.data.model.createTransactionRequest
import com.kasirkita.pos.domain.model.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CreateTransactionRequestTest {

    @Test
    fun untrackedProduct_buildsNormalRequestWithoutStockFields() {
        val cartRepository = CartRepositoryImpl()
        cartRepository.addProduct(
            Product(
                id = "product-id",
                tenantId = "tenant-id",
                categoryId = null,
                name = "Kopi Susu",
                sku = "KOPISUSU002",
                price = 15_000L,
                cost = 8_000L,
                minimumStock = 0,
                trackStock = false,
                isActive = true,
                createdAt = "2026-09-19T00:00:00.000Z",
            ),
        )
        val cartItem = cartRepository.getCart().value.items.single()
        val request = createTransactionRequest(
            clientTransactionId = "c3c3c3c3-aaaa-4444-bbbb-222222222222",
            outletId = "outlet-id",
            customerId = null,
            items = listOf(cartItem),
            paymentAmount = 15_000L,
        )
        val json = GsonBuilder().serializeNulls().create().toJsonTree(request).asJsonObject
        val requestItem = json.getAsJsonArray("items").single().asJsonObject

        assertEquals("product-id", cartItem.productId)
        assertEquals("Kopi Susu", cartItem.name)
        assertEquals("KOPISUSU002", cartItem.sku)
        assertEquals(15_000L, cartItem.price)
        assertEquals("product-id", requestItem.get("product_id").asString)
        assertEquals(1, requestItem.get("quantity").asInt)
        assertFalse(json.has("track_stock"))
        assertFalse(requestItem.has("track_stock"))
        assertFalse(requestItem.has("trackStock"))
    }
}
