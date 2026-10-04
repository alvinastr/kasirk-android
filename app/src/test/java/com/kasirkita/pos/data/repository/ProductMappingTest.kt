package com.kasirkita.pos.data.repository

import com.google.gson.Gson
import com.kasirkita.pos.data.model.ProductResponse
import com.kasirkita.pos.domain.model.SelectionMode
import org.junit.Assert.assertEquals
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

    @Test
    fun backendProductModifierPayload_preservesModesAndServerOrdering() {
        val response = gson.fromJson(
            """
            {
              "id": "product-id", "tenant_id": "tenant-id", "category_id": "category-id",
              "name": "Kopi Susu", "sku": "KOPISUSU002", "price": 15000, "cost": 8000,
              "minimum_stock": 0, "track_stock": true, "is_active": true,
              "created_at": "2026-09-19T00:00:00.000Z", "stock": 8,
              "modifier_groups": [
                {
                  "id": "single-group", "name": "Ukuran", "is_active": true, "required": true,
                  "selection_type": "SINGLE", "display_order": 1,
                  "options": [
                    { "id": "large", "modifier_group_id": "single-group", "name": "Large", "price_delta": 2000, "is_active": true, "display_order": 1 },
                    { "id": "regular", "modifier_group_id": "single-group", "name": "Regular", "price_delta": 0, "is_active": true, "display_order": 2 }
                  ]
                },
                {
                  "id": "multiple-group", "name": "Topping", "is_active": true, "required": false,
                  "selection_type": "MULTIPLE", "display_order": 2,
                  "options": []
                }
              ]
            }
            """.trimIndent(),
            ProductResponse::class.java,
        )

        val product = response.toDomain()

        assertEquals(8, product.stock)
        assertEquals(listOf("single-group", "multiple-group"), product.modifierGroups.map { it.id })
        assertTrue(product.modifierGroups.first().isActive)
        assertEquals(SelectionMode.SINGLE, product.modifierGroups.first().selectionType)
        assertEquals(SelectionMode.MULTIPLE, product.modifierGroups.last().selectionType)
        assertEquals(listOf("large", "regular"), product.modifierGroups.first().options.map { it.id })
    }

    @Test
    fun productResponse_mapsBackendNestedModifierAssignmentsToDomain() {
        val response = gson.fromJson(
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
              "track_stock": true,
              "is_active": true,
              "created_at": "2026-09-19T00:00:00.000Z",
              "stock": null,
              "modifier_groups": [
                {
                  "id": "group-id",
                  "name": "Gula",
                  "is_active": true,
                  "required": true,
                  "selection_type": "MULTIPLE",
                  "display_order": 2,
                  "options": [
                    {
                      "id": "option-id",
                      "modifier_group_id": "group-id",
                      "name": "Less Sugar",
                      "price_delta": 1000,
                      "is_active": true,
                      "display_order": 1
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
            ProductResponse::class.java,
        )

        val product = response.toDomain()
        val modifierGroup = product.modifierGroups.single()
        val option = modifierGroup.options.single()

        assertEquals(null, product.stock)
        assertEquals("group-id", modifierGroup.id)
        assertEquals("tenant-id", modifierGroup.tenantId)
        assertTrue(modifierGroup.isActive)
        assertTrue(modifierGroup.required)
        assertEquals(SelectionMode.MULTIPLE, modifierGroup.selectionType)
        assertEquals(2, modifierGroup.displayOrder)
        assertEquals("option-id", option.id)
        assertEquals("group-id", option.groupId)
        assertEquals(1_000L, option.priceDelta)
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
