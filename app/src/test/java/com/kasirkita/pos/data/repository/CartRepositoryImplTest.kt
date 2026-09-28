package com.kasirkita.pos.data.repository

import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.repository.CartUpdateResult
import org.junit.Assert.assertEquals
import org.junit.Test

class CartRepositoryImplTest {

    @Test
    fun addingTheSameProduct_preservesFieldsAndIncreasesQuantity() {
        val repository = CartRepositoryImpl()
        val product = Product(
            id = "product-id",
            tenantId = "tenant-id",
            categoryId = null,
            name = "Kopi Susu",
            sku = "KOPI-SUSU",
            price = 20_000L,
            cost = 10_000L,
            minimumStock = 5,
            trackStock = false,
            isActive = true,
            createdAt = "2026-09-17T00:00:00.000Z",
        )

        repository.addProduct(product)
        repository.addProduct(product)

        val cart = repository.getCart().value
        val item = cart.items.single()
        assertEquals(product.id, item.productId)
        assertEquals(product.name, item.name)
        assertEquals(product.sku, item.sku)
        assertEquals(20_000L, item.price)
        assertEquals(2, item.quantity)
        assertEquals(40_000L, cart.totalAmount())
    }

    @Test
    fun trackedProduct_cannotBeAddedBeyondAvailableStock() {
        val repository = CartRepositoryImpl()
        val product = product(trackStock = true)

        assertEquals(CartUpdateResult.UPDATED, repository.addProduct(product, availableStock = 2))
        assertEquals(CartUpdateResult.UPDATED, repository.addProduct(product, availableStock = 2))
        assertEquals(
            CartUpdateResult.STOCK_LIMIT_REACHED,
            repository.addProduct(product, availableStock = 2),
        )

        assertEquals(2, repository.getCart().value.items.single().quantity)
    }

    @Test
    fun trackedProduct_quantityCannotIncreaseBeyondAvailableStock() {
        val repository = CartRepositoryImpl()
        val product = product(trackStock = true)
        repository.addProduct(product, availableStock = 2)

        assertEquals(CartUpdateResult.UPDATED, repository.updateQuantity(product.id, 2))
        assertEquals(
            CartUpdateResult.STOCK_LIMIT_REACHED,
            repository.updateQuantity(product.id, 3),
        )

        assertEquals(2, repository.getCart().value.items.single().quantity)
    }

    @Test
    fun untrackedProduct_hasNoQuantityLimit() {
        val repository = CartRepositoryImpl()
        val product = product(trackStock = false)
        repository.addProduct(product)

        assertEquals(CartUpdateResult.UPDATED, repository.updateQuantity(product.id, 100))
        assertEquals(100, repository.getCart().value.items.single().quantity)
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
}
