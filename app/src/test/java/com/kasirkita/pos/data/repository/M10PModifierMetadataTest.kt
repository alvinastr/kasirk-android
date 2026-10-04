package com.kasirkita.pos.data.repository

import com.google.gson.Gson
import com.kasirkita.pos.core.database.entity.CategoryEntity
import com.kasirkita.pos.core.database.entity.ModifierGroupEntity
import com.kasirkita.pos.core.database.entity.ModifierOptionEntity
import com.kasirkita.pos.core.database.entity.ProductModifierGroupEntity
import com.kasirkita.pos.core.database.entity.toDomain
import com.kasirkita.pos.data.model.CategoryResponse
import com.kasirkita.pos.data.model.ProductResponse
import com.kasirkita.pos.domain.model.SelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M10PModifierMetadataTest {

    private val gson = Gson()

    @Test
    fun productEntity_startsWithModifierMetadataLoadedFalse() {
        val response = gson.fromJson(
            """
            {
              "id": "product-id", "tenant_id": "tenant-id", "category_id": null,
              "name": "Kopi", "sku": "KOP001", "price": 10000, "cost": 5000,
              "minimum_stock": 0, "track_stock": false, "is_active": true,
              "created_at": "2026-09-19T00:00:00.000Z"
            }
            """.trimIndent(),
            ProductResponse::class.java,
        )

        val entity = response.toEntity()

        assertFalse(entity.modifierMetadataLoaded)
    }

    @Test
    fun productResponse_withModifierGroups_setsMetadataLoadedTrue() {
        val response = gson.fromJson(
            """
            {
              "id": "product-id", "tenant_id": "tenant-id", "category_id": null,
              "name": "Kopi", "sku": "KOP001", "price": 10000, "cost": 5000,
              "minimum_stock": 0, "track_stock": false, "is_active": true,
              "created_at": "2026-09-19T00:00:00.000Z",
              "modifier_groups": []
            }
            """.trimIndent(),
            ProductResponse::class.java,
        )

        val entity = response.toEntity()

        assertTrue(entity.modifierMetadataLoaded)
    }

    @Test
    fun modifierGroupEntity_toDomain_usesAssignmentMetadata() {
        val group = ModifierGroupEntity(
            id = "group-id",
            tenantId = "tenant-id",
            name = "Ukuran",
            isActive = true,
        )
        val options = listOf(
            ModifierOptionEntity(
                id = "large",
                tenantId = "tenant-id",
                modifierGroupId = "group-id",
                name = "Large",
                priceDelta = 2000,
                isActive = true,
                displayOrder = 1,
            ),
        )

        val domain = group.toDomain(
            required = true,
            selectionType = "SINGLE",
            displayOrder = 1,
            options = options,
        )

        assertEquals("group-id", domain.id)
        assertEquals("Ukuran", domain.name)
        assertTrue(domain.required)
        assertEquals(SelectionMode.SINGLE, domain.selectionType)
        assertEquals(1, domain.displayOrder)
        assertEquals(1, domain.options.size)
    }

    @Test
    fun modifierOptionEntity_toDomain_preservesPriceDelta() {
        val option = ModifierOptionEntity(
            id = "option-id",
            tenantId = "tenant-id",
            modifierGroupId = "group-id",
            name = "Extra Shot",
            priceDelta = 5000,
            isActive = true,
            displayOrder = 1,
        )

        val domain = option.toDomain()

        assertEquals("option-id", domain.id)
        assertEquals("group-id", domain.groupId)
        assertEquals("Extra Shot", domain.name)
        assertEquals(5000L, domain.priceDelta)
        assertTrue(domain.isActive)
        assertEquals(1, domain.displayOrder)
    }

    @Test
    fun categoryEntity_toDomain_preservesIdAndName() {
        val entity = CategoryEntity(
            id = "category-id",
            tenantId = "tenant-id",
            name = "Minuman",
        )

        val domain = entity.toDomain()

        assertEquals("category-id", domain.id)
        assertEquals("tenant-id", domain.tenantId)
        assertEquals("Minuman", domain.name)
    }

    @Test
    fun productModifierGroupEntity_storesRequiredAndSelectionTypePerProduct() {
        val assignment1 = ProductModifierGroupEntity(
            tenantId = "tenant-id",
            productId = "product-1",
            modifierGroupId = "size-group",
            required = true,
            selectionType = "SINGLE",
            displayOrder = 1,
        )
        val assignment2 = ProductModifierGroupEntity(
            tenantId = "tenant-id",
            productId = "product-2",
            modifierGroupId = "size-group",
            required = false,
            selectionType = "MULTIPLE",
            displayOrder = 2,
        )

        // Same modifier group assigned to two products with different metadata
        assertEquals("size-group", assignment1.modifierGroupId)
        assertEquals("size-group", assignment2.modifierGroupId)
        assertTrue(assignment1.required)
        assertFalse(assignment2.required)
        assertEquals(SelectionMode.SINGLE.name, assignment1.selectionType)
        assertEquals(SelectionMode.MULTIPLE.name, assignment2.selectionType)
    }
}
