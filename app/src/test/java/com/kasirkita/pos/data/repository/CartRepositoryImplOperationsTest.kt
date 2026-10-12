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
    fun addingProductsToAttachedHeldOrder_preservesIdentityAndExistingLineData() {
        val heldItem = product(id = "held-item", name = "Held Item", price = 25_000L)
        val addedItem = product(id = "added-item", name = "Added Item", price = 15_000L)
        val note = "Tanpa gula"

        repo.addConfiguredProduct(heldItem, emptyList(), note)
        repo.attachHeldOrderIdentity("held-123", expectedVersion = 4, label = "Meja 2")
        repo.addProduct(addedItem)

        val cart = repo.getCart().value
        val identity = repo.getHeldOrderIdentity().value
        assertEquals("held-123", identity?.heldOrderId)
        assertEquals(4, identity?.expectedVersion)
        assertEquals("Meja 2", identity?.label)
        assertEquals(2, cart.items.size)
        assertEquals(note, cart.items.first { it.productId == "held-item" }.note)
        assertEquals(25_000L, cart.items.first { it.productId == "held-item" }.basePrice)
        assertEquals(15_000L, cart.items.first { it.productId == "added-item" }.basePrice)
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
