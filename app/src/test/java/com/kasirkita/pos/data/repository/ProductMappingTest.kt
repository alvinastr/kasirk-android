package com.kasirkita.pos.data.repository

import com.google.gson.Gson
import com.kasirkita.pos.data.model.ProductResponse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductMappingTest {

    private val gson = Gson()

    @Test
    fun untrackedApiProduct_mapsThroughCacheToUntrackedDomainProduct() {
        val response = parseProduct(trackStock = false)

        val entity = response.toEntity()
        val product = entity.toDomain()

        assertFalse(entity.trackStock)
        assertFalse(product.trackStock)
    }

    @Test
    fun trackedApiProduct_mapsThroughCacheToTrackedDomainProduct() {
        val response = parseProduct(trackStock = true)

        val entity = response.toEntity()
        val product = entity.toDomain()

        assertTrue(entity.trackStock)
        assertTrue(product.trackStock)
    }

    private fun parseProduct(trackStock: Boolean): ProductResponse = gson.fromJson(
        """
        {
          "id": "product-id",
          "tenant_id": "tenant-id",
          "category_id": null,
          "name": "Kopi Susu",
          "sku": "KOPISUSU002",
          "price": 15000,
          "cost": 8000,
          "minimum_stock": 0,
          "track_stock": $trackStock,
          "is_active": true,
          "created_at": "2026-09-19T00:00:00.000Z"
        }
        """.trimIndent(),
        ProductResponse::class.java,
    )
}
