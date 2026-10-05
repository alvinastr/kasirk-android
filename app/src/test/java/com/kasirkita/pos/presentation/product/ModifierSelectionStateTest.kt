package com.kasirkita.pos.presentation.product

import com.kasirkita.pos.domain.model.ModifierGroup
import com.kasirkita.pos.domain.model.ModifierOption
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.model.SelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModifierSelectionStateTest {
    @Test
    fun openingConfiguredProduct_startsWithNoSelectionsAndBasePrice() {
        val state = ModifierSelectionState.create(product(groups = listOf(requiredSingle())))

        assertTrue(state.selectedOptionIds.isEmpty())
        assertFalse(state.canAdd)
        assertEquals(20_000L, state.effectiveUnitPrice)
        assertNull(state.note)
    }

    @Test
    fun requiredSingle_requiresOneOptionAndReplacingSelectionKeepsOne() {
        val state = ModifierSelectionState.create(product(groups = listOf(requiredSingle())))
            .toggleOption("hot")
            .toggleOption("ice")

        assertTrue(state.canAdd)
        assertEquals(setOf("ice"), state.selectedOptionIds)
    }

    @Test
    fun optionalSingle_canReturnToNoSelection() {
        val state = ModifierSelectionState.create(product(groups = listOf(optionalSingle())))
            .toggleOption("hot")
            .toggleOption("hot")

        assertTrue(state.canAdd)
        assertTrue(state.selectedOptionIds.isEmpty())
    }

    @Test
    fun optionalMultiple_togglesIndependentlyAndUpdatesIntegerPrice() {
        val state = ModifierSelectionState.create(product(groups = listOf(optionalMultiple())))
            .toggleOption("shot")
            .toggleOption("oat")

        assertEquals(setOf("shot", "oat"), state.selectedOptionIds)
        assertEquals(32_000L, state.effectiveUnitPrice)
        assertEquals(27_000L, state.toggleOption("shot").effectiveUnitPrice)
    }

    @Test
    fun invalidConfigurationsAndInactiveChoices_cannotBeAddedOrSelected() {
        val unsupported = ModifierSelectionState.create(product(groups = listOf(requiredMultiple())))
        val noOptions = ModifierSelectionState.create(product(groups = listOf(requiredSingle(options = emptyList()))))
        val inactive = ModifierSelectionState.create(product(groups = listOf(requiredSingle(options = listOf(option("off", "temp", active = false))))) )

        assertFalse(unsupported.canAdd)
        assertEquals("Konfigurasi opsi tidak didukung", unsupported.groupFeedback("addons"))
        assertFalse(noOptions.canAdd)
        assertEquals("Pilih 1 opsi", noOptions.groupFeedback("temp"))
        assertTrue(inactive.toggleOption("off").selectedOptionIds.isEmpty())
    }

    @Test
    fun inactiveRequiredGroupDoesNotBlockAndOrderingAndSnapshotsAreStable() {
        val state = ModifierSelectionState.create(product(groups = listOf(
            requiredSingle(id = "later", order = 2),
            requiredSingle(id = "inactive", order = 0, active = false),
            optionalMultiple(id = "first", order = 1),
        ))).toggleOption("shot").toggleOption("ice")

        assertEquals(listOf("first", "later"), state.groups.map { it.id })
        assertEquals(listOf("shot", "oat"), state.groups.first().options.map { it.id })
        assertTrue(state.canAdd)
        assertEquals(listOf("first:shot", "later:ice"), state.selectedSnapshots().map { "${it.groupId}:${it.optionId}" })
    }

    @Test
    fun noteTrimsBlankToNullAnd255IsMaximumWithMeaningfulWhitespacePreserved() {
        val state = ModifierSelectionState.create(product(groups = listOf(optionalSingle())))

        assertEquals(null, state.updateNote("").note)
        assertEquals(null, state.updateNote("   ").note)
        assertEquals(null, state.updateNote("\t\n").note)

        val note254 = "a".repeat(254)
        val updated254 = state.updateNote(note254)
        assertEquals(note254, updated254.note)
        assertNull(updated254.noteFeedback)

        val note255 = "a".repeat(255)
        val updated255 = state.updateNote(note255)
        assertEquals(note255, updated255.note)
        assertNull(updated255.noteFeedback)

        val note256 = "a".repeat(256)
        val updated256 = state.updateNote(note256)
        assertNull(updated256.note)
        assertEquals("Catatan maksimal 255 karakter", updated256.noteFeedback)

        val meaningful = "  internal  spaces  preserved  "
        assertEquals("internal  spaces  preserved", state.updateNote(meaningful).note)
    }

    private fun product(groups: List<ModifierGroup>) = Product(
        id = "coffee", tenantId = "tenant", categoryId = null, name = "Americano", sku = "AMR",
        price = 20_000L, cost = 0L, minimumStock = 0, trackStock = false, isActive = true,
        createdAt = "2026-10-05T00:00:00Z", modifierMetadataLoaded = true, modifierGroups = groups,
    )

    private fun requiredSingle(
        id: String = "temp", order: Int = 1, active: Boolean = true,
        options: List<ModifierOption> = listOf(option("ice", id, 2), option("hot", id, 1)),
    ) = ModifierGroup(id, "tenant", "Suhu", active, order, true, SelectionMode.SINGLE, options)

    private fun optionalSingle() = ModifierGroup(
        "temp", "tenant", "Suhu", true, 1, false, SelectionMode.SINGLE,
        listOf(option("ice", "temp", 2), option("hot", "temp", 1)),
    )

    private fun optionalMultiple(id: String = "addons", order: Int = 3) = ModifierGroup(
        id, "tenant", "Add-on", true, order, false, SelectionMode.MULTIPLE,
        listOf(option("oat", id, 2, 7_000L), option("shot", id, 1, 5_000L)),
    )

    private fun requiredMultiple() = ModifierGroup(
        "addons", "tenant", "Add-on", true, 1, true, SelectionMode.MULTIPLE, listOf(option("shot", "addons", 1)),
    )

    private fun option(id: String, groupId: String, order: Int = 1, delta: Long = 0L, active: Boolean = true) =
        ModifierOption(id, groupId, id, delta, active, order)
}
