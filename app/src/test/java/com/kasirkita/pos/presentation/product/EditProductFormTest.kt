package com.kasirkita.pos.presentation.product

import com.kasirkita.pos.domain.model.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditProductFormTest {

    @Test
    fun existingProduct_populatesEveryFormField() {
        val values = product().toProductFormInitialValues()

        assertEquals("Kopi Susu", values.name)
        assertEquals("KOPISUSU002", values.sku)
        assertEquals("15000", values.price)
        assertEquals("8000", values.cost)
        assertEquals("2", values.minimumStock)
        assertEquals("category-id", values.categoryId)
        assertFalse(values.trackStock)
    }

    @Test
    fun unchangedForm_producesNoPatchFields() {
        val original = product()

        val changes = validatedProduct().changesFrom(original)

        assertFalse(changes.hasChanges)
        assertNull(changes.name)
        assertNull(changes.sku)
        assertFalse(changes.categoryIdChanged)
        assertNull(changes.price)
        assertNull(changes.cost)
        assertNull(changes.minimumStock)
        assertNull(changes.trackStock)
    }

    @Test
    fun changedFields_produceSparsePatchValues() {
        val changes = validatedProduct(
            name = "Kopi Susu Aren",
            price = 17_000L,
            trackStock = true,
        ).changesFrom(product())

        assertTrue(changes.hasChanges)
        assertEquals("Kopi Susu Aren", changes.name)
        assertEquals(17_000L, changes.price)
        assertTrue(changes.trackStock ?: false)
        assertNull(changes.sku)
        assertNull(changes.cost)
        assertNull(changes.minimumStock)
        assertFalse(changes.categoryIdChanged)
    }

    @Test
    fun removingCategory_marksCategoryAsExplicitlyChanged() {
        val changes = validatedProduct(categoryId = null).changesFrom(product())

        assertTrue(changes.hasChanges)
        assertTrue(changes.categoryIdChanged)
        assertNull(changes.categoryId)
    }

    private fun product() = Product(
        id = "product-id",
        tenantId = "tenant-id",
        categoryId = "category-id",
        name = "Kopi Susu",
        sku = "KOPISUSU002",
        price = 15_000L,
        cost = 8_000L,
        minimumStock = 2,
        trackStock = false,
        isActive = true,
        createdAt = "2026-09-22T00:00:00.000Z",
    )

    private fun validatedProduct(
        name: String = "Kopi Susu",
        sku: String = "KOPISUSU002",
        categoryId: String? = "category-id",
        price: Long = 15_000L,
        cost: Long = 8_000L,
        minimumStock: Int = 2,
        trackStock: Boolean = false,
    ) = ValidatedCreateProduct(
        name = name,
        sku = sku,
        price = price,
        cost = cost,
        minimumStock = minimumStock,
        categoryId = categoryId,
        trackStock = trackStock,
    )
}
