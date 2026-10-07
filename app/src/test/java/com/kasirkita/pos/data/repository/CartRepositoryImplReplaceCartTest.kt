package com.kasirkita.pos.data.repository

import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.CartLineKey
import com.kasirkita.pos.domain.model.CartModifierSelectionSnapshot
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.repository.CartUpdateResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Atomic cart replacement contract required by M16F Held Order restoration.
 *
 * Restoration must be all-or-nothing: every candidate line is validated against
 * current stock metadata BEFORE any mutation, so no partially restored cart is
 * ever observable, and an existing non-empty cart survives a failed restore.
 */
class CartRepositoryImplReplaceCartTest {

    private lateinit var repo: CartRepositoryImpl

    @Before
    fun setup() {
        repo = CartRepositoryImpl()
    }

    private fun line(
        productId: String = "coffee",
        quantity: Int = 1,
        modifiers: List<CartModifierSelectionSnapshot> = emptyList(),
        note: String? = null,
        basePrice: Long = 20_000L,
        trackStock: Boolean = false,
        availableStock: Int? = null,
    ): CartItem = CartItem(
        lineKey = CartLineKey.from(productId, modifiers.map { it.optionId }, note),
        productId = productId,
        name = "Americano",
        sku = "AMR",
        basePrice = basePrice,
        quantity = quantity,
        modifierSelections = modifiers,
        note = note,
        trackStock = trackStock,
        availableStock = availableStock,
    )

    private fun seedExistingCart() {
        repo.addConfiguredProduct(
            Product(
                id = "tea",
                tenantId = "tenant-1",
                categoryId = null,
                name = "Teh",
                sku = "TEH",
                price = 10_000L,
                cost = 0L,
                minimumStock = 0,
                trackStock = false,
                isActive = true,
                createdAt = "2026-01-01T00:00:00Z",
            ),
            emptyList(),
            null,
        )
    }

    @Test
    fun replaceCart_restoresExactQuantityWithoutIncrementalAdds() {
        val result = repo.replaceCart(listOf(line(productId = "coffee", quantity = 7)))

        assertEquals(CartUpdateResult.UPDATED, result)
        val restored = repo.getCart().value.items.single()
        assertEquals(7, restored.quantity)
        assertEquals(1, repo.getCart().value.items.size)
    }

    @Test
    fun replaceCart_preservesExplicitQuantityAcrossMultipleLines() {
        val result = repo.replaceCart(
            listOf(
                line(productId = "coffee", quantity = 3),
                line(productId = "tea", quantity = 2),
                line(productId = "juice", quantity = 5),
            ),
        )

        assertEquals(CartUpdateResult.UPDATED, result)
        val byProduct = repo.getCart().value.items.associateBy { it.productId }
        assertEquals(3, byProduct.getValue("coffee").quantity)
        assertEquals(2, byProduct.getValue("tea").quantity)
        assertEquals(5, byProduct.getValue("juice").quantity)
    }

    @Test
    fun replaceCart_preservesCurrentProductStockMetadata() {
        val result = repo.replaceCart(
            listOf(
                line(
                    productId = "coffee",
                    quantity = 2,
                    trackStock = true,
                    availableStock = 9,
                ),
            ),
        )

        assertEquals(CartUpdateResult.UPDATED, result)
        val restored = repo.getCart().value.items.single()
        assertTrue("current trackStock must be preserved", restored.trackStock)
        assertEquals(9, restored.availableStock)
    }

    @Test
    fun replaceCart_preservesModifierIdentitiesAndNotes() {
        val ice = CartModifierSelectionSnapshot("ice", "temp", "Temperature", "Ice", 0L)
        val less = CartModifierSelectionSnapshot("less-sugar", "sugar", "Sugar", "Less Sugar", 0L)

        val result = repo.replaceCart(
            listOf(
                line(
                    productId = "coffee",
                    quantity = 4,
                    modifiers = listOf(ice, less),
                    note = "Less hot",
                ),
            ),
        )

        assertEquals(CartUpdateResult.UPDATED, result)
        val restored = repo.getCart().value.items.single()
        assertEquals(4, restored.quantity)
        assertEquals(listOf("ice", "less-sugar"), restored.modifierSelections.map { it.optionId })
        assertEquals(listOf("ice", "less-sugar"), restored.modifierOptionIds)
        assertEquals("Less hot", restored.note)
    }

    @Test
    fun replaceCart_insufficientStockCausesZeroMutationAndKeepsExistingCart() {
        seedExistingCart()
        val before = repo.getCart().value

        val result = repo.replaceCart(
            listOf(
                line(productId = "coffee", quantity = 2, trackStock = true, availableStock = 8),
                line(productId = "tea", quantity = 99, trackStock = true, availableStock = 3),
            ),
        )

        assertEquals(CartUpdateResult.STOCK_LIMIT_REACHED, result)
        assertEquals("failed restore must not mutate the cart at all", before, repo.getCart().value)
    }

    @Test
    fun replaceCart_replacingWithEmptyListClearsCart() {
        seedExistingCart()

        val result = repo.replaceCart(emptyList())

        assertEquals(CartUpdateResult.UPDATED, result)
        assertTrue(repo.getCart().value.items.isEmpty())
    }

    @Test
    fun replaceCart_untrackedProductIsNotStockLimited() {
        val result = repo.replaceCart(
            listOf(line(productId = "coffee", quantity = 500, trackStock = false, availableStock = 1)),
        )

        assertEquals(CartUpdateResult.UPDATED, result)
        assertEquals(500, repo.getCart().value.items.single().quantity)
    }

    @Test
    fun replaceCart_nullAvailableStockIsNotStockLimited() {
        val result = repo.replaceCart(
            listOf(line(productId = "coffee", quantity = 42, trackStock = true, availableStock = null)),
        )

        assertEquals(CartUpdateResult.UPDATED, result)
        assertEquals(42, repo.getCart().value.items.single().quantity)
    }

    @Test
    fun replaceCart_quantityEqualToAvailableStockIsAllowed() {
        val result = repo.replaceCart(
            listOf(line(productId = "coffee", quantity = 5, trackStock = true, availableStock = 5)),
        )

        assertEquals(CartUpdateResult.UPDATED, result)
        assertEquals(5, repo.getCart().value.items.single().quantity)
    }

    @Test
    fun replaceCart_replacesPreviousContentsEntirely() {
        seedExistingCart()

        val result = repo.replaceCart(listOf(line(productId = "coffee", quantity = 1)))

        assertEquals(CartUpdateResult.UPDATED, result)
        assertEquals(listOf("coffee"), repo.getCart().value.items.map { it.productId })
    }

    @Test
    fun replaceCart_preservesCurrentCatalogPriceNotSnapshotPrice() {
        // Restored line carries the CURRENT catalog price; snapshot prices are
        // display-only and must never become the working cart's authoritative price.
        val result = repo.replaceCart(listOf(line(productId = "coffee", basePrice = 33_000L, quantity = 2)))

        assertEquals(CartUpdateResult.UPDATED, result)
        val restored = repo.getCart().value.items.single()
        assertEquals(33_000L, restored.price)
        assertEquals(66_000L, restored.subtotal())
    }
}
