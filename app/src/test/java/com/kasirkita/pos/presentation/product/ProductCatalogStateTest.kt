package com.kasirkita.pos.presentation.product

import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.model.Stock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductCatalogStateTest {

    @Test
    fun trackedProduct_mapsOutletStockQuantity() {
        val item = mapProductsWithStock(
            products = listOf(product(trackStock = true)),
            stocks = listOf(stock(quantity = 7)),
        ).single()

        assertEquals(7, item.stockQuantity)
        assertTrue(item.canAddToCart)
    }

    @Test
    fun trackedProductWithZeroStock_cannotBeAdded() {
        val item = mapProductsWithStock(
            products = listOf(product(trackStock = true)),
            stocks = listOf(stock(quantity = 0)),
        ).single()

        assertEquals(0, item.stockQuantity)
        assertFalse(item.canAddToCart)
    }

    @Test
    fun trackedProductWithoutStockRow_isTreatedAsZeroStock() {
        val item = mapProductsWithStock(
            products = listOf(product(trackStock = true)),
            stocks = emptyList(),
        ).single()

        assertEquals(0, item.stockQuantity)
        assertFalse(item.canAddToCart)
    }

    @Test
    fun untrackedProduct_doesNotExposeQuantityAndCanBeAdded() {
        val item = mapProductsWithStock(
            products = listOf(product(trackStock = false)),
            stocks = listOf(stock(quantity = 0)),
        ).single()

        assertNull(item.stockQuantity)
        assertTrue(item.canAddToCart)
    }

    private fun product(trackStock: Boolean) = Product(
        id = "product-id",
        tenantId = "tenant-id",
        categoryId = null,
        name = "Kopi Susu",
        sku = "KOPI-SUSU",
        price = 20_000L,
        cost = 10_000L,
        minimumStock = 5,
        trackStock = trackStock,
        isActive = true,
        createdAt = "2026-09-17T00:00:00.000Z",
    )

    private fun stock(quantity: Int) = Stock(
        id = "stock-id",
        outletId = "outlet-id",
        productId = "product-id",
        quantity = quantity,
        updatedAt = "2026-09-28T00:00:00.000Z",
    )
}
