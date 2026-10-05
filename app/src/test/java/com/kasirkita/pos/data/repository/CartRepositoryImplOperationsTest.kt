package com.kasirkita.pos.data.repository

import com.kasirkita.pos.domain.model.CartLineKey
import com.kasirkita.pos.domain.model.CartModifierSelectionSnapshot
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.repository.CartUpdateResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class CartRepositoryImplOperationsTest {
    private lateinit var repo: CartRepositoryImpl

    private fun product(
        id: String = "coffee",
        name: String = "Americano",
        sku: String = "AMR",
        price: Long = 20_000L,
    ) = Product(
        id = id,
        tenantId = "tenant-1",
        categoryId = null,
        name = name,
        sku = sku,
        price = price,
        cost = 0L,
        minimumStock = 0,
        trackStock = false,
        isActive = true,
        createdAt = "2026-01-01T00:00:00Z",
    )

    @Before
    fun setup() {
        repo = CartRepositoryImpl()
    }

    @Test
    fun addSameConfigurationTwice_mergesOntoOneLine() {
        val product = product()
        val ice = CartModifierSelectionSnapshot("ice", "temp", "Temperature", "Ice", 0L)

        repo.addConfiguredProduct(product, listOf(ice), null)
        repo.addConfiguredProduct(product, listOf(ice), null)

        val cart = repo.getCart().value
        assertEquals(1, cart.items.size)
        assertEquals(2, cart.items[0].quantity)
    }

    @Test
    fun addDifferentModifiers_createsSeperateLine() {
        val product = product()
        val ice = CartModifierSelectionSnapshot("ice", "temp", "Temperature", "Ice", 0L)
        val hot = CartModifierSelectionSnapshot("hot", "temp", "Temperature", "Hot", 0L)

        repo.addConfiguredProduct(product, listOf(ice), null)
        repo.addConfiguredProduct(product, listOf(hot), null)

        val cart = repo.getCart().value
        assertEquals(2, cart.items.size)
    }

    @Test
    fun addDifferentNotes_createsSeperateLine() {
        val product = product()
        val ice = CartModifierSelectionSnapshot("ice", "temp", "Temperature", "Ice", 0L)

        repo.addConfiguredProduct(product, listOf(ice), "Sedikit es")
        repo.addConfiguredProduct(product, listOf(ice), "Tanpa es")

        val cart = repo.getCart().value
        assertEquals(2, cart.items.size)
    }

    @Test
    fun incrementTargetsExactLine() {
        val product = product()
        val ice = CartModifierSelectionSnapshot("ice", "temp", "Temperature", "Ice", 0L)
        val hot = CartModifierSelectionSnapshot("hot", "temp", "Temperature", "Hot", 0L)

        repo.addConfiguredProduct(product, listOf(ice), null)
        repo.addConfiguredProduct(product, listOf(hot), null)

        val iceKey = repo.getCart().value.items[0].lineKey.value
        val result = repo.updateQuantity(iceKey, 5)

        assertEquals(CartUpdateResult.UPDATED, result)
        val cart = repo.getCart().value
        val iceItem = cart.items.find { it.lineKey.value == iceKey }!!
        assertEquals(5, iceItem.quantity)
        assertEquals(1, cart.items.find { it.modifierOptionIds.contains("hot") }!!.quantity)
    }

    @Test
    fun noModifierNoNote_linesDoNotMergeWithModifiers() {
        val product = product()
        val ice = CartModifierSelectionSnapshot("ice", "temp", "Temperature", "Ice", 0L)

        repo.addProduct(product)
        repo.addConfiguredProduct(product, listOf(ice), null)

        val cart = repo.getCart().value
        assertEquals(2, cart.items.size)
    }

    @Test
    fun removeTargetsExactLine() {
        val product = product()
        val ice = CartModifierSelectionSnapshot("ice", "temp", "Temperature", "Ice", 0L)
        val hot = CartModifierSelectionSnapshot("hot", "temp", "Temperature", "Hot", 0L)

        repo.addConfiguredProduct(product, listOf(ice), null)
        repo.addConfiguredProduct(product, listOf(hot), null)

        val iceKey = repo.getCart().value.items[0].lineKey.value
        repo.removeProduct(iceKey)

        val cart = repo.getCart().value
        assertEquals(1, cart.items.size)
        assertFalse(cart.items.any { it.modifierOptionIds.contains("ice") })
    }
}
