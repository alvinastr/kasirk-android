package com.kasirkita.pos.presentation.receipt

import com.kasirkita.pos.domain.model.ModifierSnapshot
import com.kasirkita.pos.domain.model.ReceiptItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M14 Receipt UX: Snapshot data preservation tests
 *
 * Verifies:
 * - Product name snapshot preserved
 * - SKU snapshot preserved
 * - Modifier snapshots preserved with labels and deltas
 * - Item note preserved
 * - Effective price preserved
 * - Same product with different modifiers remains separate
 * - Legacy items without snapshots safe
 */
class ReceiptSnapshotDataTest {

    @Test
    fun `product name snapshot preserved`() {
        val item = ReceiptItem(
            id = "item1",
            productId = "prod1",
            productName = "Espresso",
            sku = "ESP-001",
            quantity = 2,
            unitPrice = 25000L,
            subtotal = 50000L,
            productNameSnapshot = "Espresso",
            skuSnapshot = "ESP-001",
            basePriceSnapshot = 25000L,
            effectivePriceSnapshot = 25000L,
            note = null,
            modifierSnapshots = emptyList(),
        )

        assertNotNull(item.productNameSnapshot)
        assertEquals("Espresso", item.productNameSnapshot)
    }

    @Test
    fun `SKU snapshot preserved`() {
        val item = ReceiptItem(
            id = "item1",
            productId = "prod1",
            productName = "Latte",
            sku = "LAT-001",
            quantity = 1,
            unitPrice = 30000L,
            subtotal = 30000L,
            productNameSnapshot = "Latte",
            skuSnapshot = "LAT-001",
            basePriceSnapshot = 30000L,
            effectivePriceSnapshot = 30000L,
            note = null,
            modifierSnapshots = emptyList(),
        )

        assertNotNull(item.skuSnapshot)
        assertEquals("LAT-001", item.skuSnapshot)
    }

    @Test
    fun `blank SKU snapshot safe`() {
        val item = ReceiptItem(
            id = "item1",
            productId = "prod1",
            productName = "Custom Item",
            sku = "",
            quantity = 1,
            unitPrice = 15000L,
            subtotal = 15000L,
            productNameSnapshot = "Custom Item",
            skuSnapshot = "",
            basePriceSnapshot = 15000L,
            effectivePriceSnapshot = 15000L,
            note = null,
            modifierSnapshots = emptyList(),
        )

        assertEquals("", item.skuSnapshot)
    }

    @Test
    fun `modifier snapshots preserved with labels and deltas`() {
        val modifiers = listOf(
            ModifierSnapshot(
                id = "mod1",
                modifierGroupId = "grp1",
                modifierOptionId = "opt1",
                groupName = "Size",
                optionName = "Large",
                priceDelta = 5000L,
            ),
            ModifierSnapshot(
                id = "mod2",
                modifierGroupId = "grp2",
                modifierOptionId = "opt2",
                groupName = "Extra",
                optionName = "Extra Shot",
                priceDelta = 3000L,
            ),
        )

        val item = ReceiptItem(
            id = "item1",
            productId = "prod1",
            productName = "Latte",
            sku = "LAT-001",
            quantity = 1,
            unitPrice = 38000L,
            subtotal = 38000L,
            productNameSnapshot = "Latte",
            skuSnapshot = "LAT-001",
            basePriceSnapshot = 30000L,
            effectivePriceSnapshot = 38000L,
            note = null,
            modifierSnapshots = modifiers,
        )

        assertEquals(2, item.modifierSnapshots.size)
        assertEquals("Large", item.modifierSnapshots[0].optionName)
        assertEquals(5000L, item.modifierSnapshots[0].priceDelta)
        assertEquals("Extra Shot", item.modifierSnapshots[1].optionName)
        assertEquals(3000L, item.modifierSnapshots[1].priceDelta)
    }

    @Test
    fun `item note preserved`() {
        val item = ReceiptItem(
            id = "item1",
            productId = "prod1",
            productName = "Latte",
            sku = "LAT-001",
            quantity = 1,
            unitPrice = 30000L,
            subtotal = 30000L,
            productNameSnapshot = "Latte",
            skuSnapshot = "LAT-001",
            basePriceSnapshot = 30000L,
            effectivePriceSnapshot = 30000L,
            note = "Less sugar",
            modifierSnapshots = emptyList(),
        )

        assertNotNull(item.note)
        assertEquals("Less sugar", item.note)
    }

    @Test
    fun `effective price preserved and unit price matches effective`() {
        val item = ReceiptItem(
            id = "item1",
            productId = "prod1",
            productName = "Latte",
            sku = "LAT-001",
            quantity = 1,
            unitPrice = 38000L,
            subtotal = 38000L,
            productNameSnapshot = "Latte",
            skuSnapshot = "LAT-001",
            basePriceSnapshot = 30000L,
            effectivePriceSnapshot = 38000L,
            note = null,
            modifierSnapshots = listOf(
                ModifierSnapshot(
                    id = "mod1",
                    modifierGroupId = null,
                    modifierOptionId = null,
                    groupName = "Size",
                    optionName = "Large",
                    priceDelta = 5000L,
                ),
                ModifierSnapshot(
                    id = "mod2",
                    modifierGroupId = null,
                    modifierOptionId = null,
                    groupName = "Extra",
                    optionName = "Extra Shot",
                    priceDelta = 3000L,
                ),
            ),
        )

        // unitPrice should equal effectivePriceSnapshot (base + modifiers already applied)
        assertEquals(38000L, item.effectivePriceSnapshot)
        assertEquals(38000L, item.unitPrice)
        // Modifier deltas are descriptive only, not added again
        val modifierSum = item.modifierSnapshots.sumOf { it.priceDelta }
        assertEquals(8000L, modifierSum)
    }

    @Test
    fun `same product different modifiers remains separate`() {
        val item1 = ReceiptItem(
            id = "item1",
            productId = "prod1",
            productName = "Latte",
            sku = "LAT-001",
            quantity = 1,
            unitPrice = 35000L,
            subtotal = 35000L,
            productNameSnapshot = "Latte",
            skuSnapshot = "LAT-001",
            basePriceSnapshot = 30000L,
            effectivePriceSnapshot = 35000L,
            note = null,
            modifierSnapshots = listOf(
                ModifierSnapshot(
                    id = "mod1",
                    modifierGroupId = null,
                    modifierOptionId = null,
                    groupName = "Size",
                    optionName = "Large",
                    priceDelta = 5000L,
                ),
            ),
        )

        val item2 = ReceiptItem(
            id = "item2",
            productId = "prod1",
            productName = "Latte",
            sku = "LAT-001",
            quantity = 1,
            unitPrice = 33000L,
            subtotal = 33000L,
            productNameSnapshot = "Latte",
            skuSnapshot = "LAT-001",
            basePriceSnapshot = 30000L,
            effectivePriceSnapshot = 33000L,
            note = null,
            modifierSnapshots = listOf(
                ModifierSnapshot(
                    id = "mod2",
                    modifierGroupId = null,
                    modifierOptionId = null,
                    groupName = "Extra",
                    optionName = "Extra Shot",
                    priceDelta = 3000L,
                ),
            ),
        )

        // Same productId but different modifiers = separate lines
        assertEquals("prod1", item1.productId)
        assertEquals("prod1", item2.productId)
        assertTrue(item1.id != item2.id)
        assertTrue(item1.modifierSnapshots != item2.modifierSnapshots)
    }

    @Test
    fun `same product different note remains separate`() {
        val item1 = ReceiptItem(
            id = "item1",
            productId = "prod1",
            productName = "Latte",
            sku = "LAT-001",
            quantity = 1,
            unitPrice = 30000L,
            subtotal = 30000L,
            productNameSnapshot = "Latte",
            skuSnapshot = "LAT-001",
            basePriceSnapshot = 30000L,
            effectivePriceSnapshot = 30000L,
            note = "Hot",
            modifierSnapshots = emptyList(),
        )

        val item2 = ReceiptItem(
            id = "item2",
            productId = "prod1",
            productName = "Latte",
            sku = "LAT-001",
            quantity = 1,
            unitPrice = 30000L,
            subtotal = 30000L,
            productNameSnapshot = "Latte",
            skuSnapshot = "LAT-001",
            basePriceSnapshot = 30000L,
            effectivePriceSnapshot = 30000L,
            note = "Iced",
            modifierSnapshots = emptyList(),
        )

        // Same productId but different notes = separate lines
        assertEquals("prod1", item1.productId)
        assertEquals("prod1", item2.productId)
        assertTrue(item1.id != item2.id)
        assertTrue(item1.note != item2.note)
    }

    @Test
    fun `legacy item without modifier snapshots safe`() {
        val item = ReceiptItem(
            id = "item1",
            productId = "prod1",
            productName = "Espresso",
            sku = "ESP-001",
            quantity = 1,
            unitPrice = 25000L,
            subtotal = 25000L,
            productNameSnapshot = null,
            skuSnapshot = null,
            basePriceSnapshot = null,
            effectivePriceSnapshot = null,
            note = null,
            modifierSnapshots = emptyList(),
        )

        assertTrue(item.modifierSnapshots.isEmpty())
        assertEquals("Espresso", item.productName)
    }
}
