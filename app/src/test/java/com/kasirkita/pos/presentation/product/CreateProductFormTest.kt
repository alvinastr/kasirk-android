package com.kasirkita.pos.presentation.product

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateProductFormTest {

    @Test
    fun emptyRequiredFields_returnFriendlyValidationErrors() {
        val result = validateCreateProductForm(
            form(name = " ", sku = "", price = ""),
        ) as CreateProductFormResult.Invalid

        assertEquals("Nama produk wajib diisi.", result.errors.name)
        assertEquals("SKU wajib diisi.", result.errors.sku)
        assertEquals("Harga jual wajib diisi.", result.errors.price)
    }

    @Test
    fun invalidNumbers_returnFieldSpecificErrors() {
        val result = validateCreateProductForm(
            form(
                price = "lima belas ribu",
                cost = "delapan ribu",
                minimumStock = "dua",
            ),
        ) as CreateProductFormResult.Invalid

        assertEquals("Harga jual harus berupa angka.", result.errors.price)
        assertEquals("Harga modal harus berupa angka.", result.errors.cost)
        assertEquals(
            "Stok minimum harus berupa angka bulat.",
            result.errors.minimumStock,
        )
    }

    @Test
    fun optionalNumericFields_whenBlank_mapToZero() {
        val result = validateCreateProductForm(
            form(cost = "", minimumStock = ""),
        ) as CreateProductFormResult.Valid

        assertEquals(0L, result.product.cost)
        assertEquals(0, result.product.minimumStock)
    }

    @Test
    fun validForm_preservesCreateRequestValues() {
        val result = validateCreateProductForm(
            form(
                name = "  Kopi Susu  ",
                sku = " KOPISUSU002 ",
                price = "15000",
                cost = "8000",
                minimumStock = "2",
                categoryId = " category-id ",
                trackStock = false,
            ),
        ) as CreateProductFormResult.Valid

        assertEquals("Kopi Susu", result.product.name)
        assertEquals("KOPISUSU002", result.product.sku)
        assertEquals(15_000L, result.product.price)
        assertEquals(8_000L, result.product.cost)
        assertEquals(2, result.product.minimumStock)
        assertEquals("category-id", result.product.categoryId)
        assertTrue(!result.product.trackStock)
    }

    @Test
    fun noCategory_mapsToNullCategoryId() {
        val result = validateCreateProductForm(
            form(categoryId = null),
        ) as CreateProductFormResult.Valid

        assertNull(result.product.categoryId)
    }

    private fun form(
        name: String = "Kopi Susu",
        sku: String = "KOPISUSU002",
        price: String = "15000",
        cost: String = "8000",
        minimumStock: String = "0",
        categoryId: String? = null,
        trackStock: Boolean = true,
    ) = CreateProductFormInput(
        name = name,
        sku = sku,
        price = price,
        cost = cost,
        minimumStock = minimumStock,
        categoryId = categoryId,
        trackStock = trackStock,
    )
}
