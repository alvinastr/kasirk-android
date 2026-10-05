package com.kasirkita.pos.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CartLineConfigurationTest {

    @Test
    fun noModifiersNoNote_identityIsDeterministicAndLegacyReadable() {
        val first = CartLineKey.from("product-id", emptyList(), null)
        val second = CartLineKey.from("product-id", emptyList(), "   ")

        assertEquals(first, second)
        assertEquals("product-id", first.value)
    }

    @Test
    fun sameOptionsDifferentOrder_haveSameIdentity() {
        val first = CartLineKey.from("product-id", listOf("option-b", "option-a"), null)
        val second = CartLineKey.from("product-id", listOf("option-a", "option-b"), null)

        assertEquals(first, second)
        assertEquals(listOf("option-a", "option-b"), first.modifierOptionIds)
    }

    @Test
    fun duplicateOptionIds_normalizeSafely() {
        val key = CartLineKey.from("product-id", listOf("option-b", "option-a", "option-b"), null)

        assertEquals(listOf("option-a", "option-b"), key.modifierOptionIds)
    }

    @Test
    fun differentOptions_haveDifferentIdentity() {
        val hot = CartLineKey.from("product-id", listOf("hot"), null)
        val ice = CartLineKey.from("product-id", listOf("ice"), null)

        assertNotEquals(hot, ice)
    }

    @Test
    fun blankNoteEqualsNullNote() {
        val blank = CartLineKey.from("product-id", emptyList(), "   ")
        val nullNote = CartLineKey.from("product-id", emptyList(), null)

        assertEquals(blank, nullNote)
        assertNull(blank.note)
    }

    @Test
    fun trimmedEquivalentNote_hasSameIdentity() {
        val first = CartLineKey.from("product-id", emptyList(), " Sedikit es ")
        val second = CartLineKey.from("product-id", emptyList(), "Sedikit es")

        assertEquals(first, second)
        assertEquals("Sedikit es", first.note)
    }

    @Test
    fun differentMeaningfulNote_hasDifferentIdentity() {
        val first = CartLineKey.from("product-id", emptyList(), "Sedikit es")
        val second = CartLineKey.from("product-id", emptyList(), "Tanpa es")

        assertNotEquals(first, second)
    }

    @Test
    fun canonicalIdentity_isLengthPrefixedToAvoidNaiveConcatenationCollision() {
        val first = CartLineKey.from("ab", listOf("c"), null)
        val second = CartLineKey.from("a", listOf("bc"), null)

        assertNotEquals(first.value, second.value)
        assertEquals("p:2:ab|m:1:1:c;|n:null", first.value)
        assertEquals("p:1:a|m:1:2:bc;|n:null", second.value)
    }

    @Test
    fun noteLongerThan255_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CartLineKey.from("product-id", emptyList(), "x".repeat(256))
        }
    }

    @Test
    fun cartItemEffectivePriceAndSubtotal_useIntegerRupiah() {
        val item = CartItem(
            lineKey = CartLineKey.from("product-id", listOf("shot", "ice"), null),
            productId = "product-id",
            name = "Americano",
            sku = "AMR",
            basePrice = 20_000L,
            quantity = 3,
            modifierSelections = listOf(
                CartModifierSelectionSnapshot("shot", "addons", "Add-ons", "Extra Shot", 5_000L),
                CartModifierSelectionSnapshot("ice", "temp", "Temperature", "Ice", 0L),
            ),
        )

        assertEquals(25_000L, item.price)
        assertEquals(75_000L, item.subtotal())
    }

    @Test
    fun cartItemWithOneAndMultipleDeltas_sumsEffectivePrice() {
        val oneDelta = cartItemWithDeltas(1_500L)
        val twoDeltas = cartItemWithDeltas(1_500L, 2_000L)

        assertEquals(11_500L, oneDelta.price)
        assertEquals(13_500L, twoDeltas.price)
    }

    private fun cartItemWithDeltas(vararg deltas: Long): CartItem = CartItem(
        lineKey = CartLineKey.from("product-id", deltas.indices.map { "option-$it" }, null),
        productId = "product-id",
        name = "Product",
        sku = "SKU",
        basePrice = 10_000L,
        quantity = 1,
        modifierSelections = deltas.mapIndexed { index, delta ->
            CartModifierSelectionSnapshot(
                optionId = "option-$index",
                groupId = "group",
                groupName = "Group",
                optionName = "Option $index",
                priceDelta = delta,
            )
        },
    )
}
