package com.kasirkita.pos.presentation.product

import com.kasirkita.pos.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryOptionsTest {

    @Test
    fun emptyCategories_keepsNoCategoryOptionAvailable() {
        val options = categoryOptions(emptyList())

        assertEquals(1, options.size)
        assertNull(options.single().id)
        assertEquals("Tanpa kategori", options.single().label)
    }

    @Test
    fun selectedCategoryId_resolvesExistingCategoryName() {
        val options = categoryOptions(
            listOf(
                Category(
                    id = "category-id",
                    tenantId = "tenant-id",
                    name = "Minuman",
                    createdAt = "2026-09-23T00:00:00.000Z",
                ),
            ),
        )

        assertEquals(
            "Minuman",
            categorySelectionLabel("category-id", options),
        )
    }
}
