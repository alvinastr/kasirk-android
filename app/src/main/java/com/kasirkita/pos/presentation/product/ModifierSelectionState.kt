package com.kasirkita.pos.presentation.product

import com.kasirkita.pos.domain.model.CartModifierSelectionSnapshot
import com.kasirkita.pos.domain.model.ModifierGroup
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.model.SelectionMode

data class ModifierSelectionState private constructor(
    val product: Product,
    val groups: List<ModifierGroup>,
    val selectedOptionIds: Set<String>,
    val note: String?,
    val noteFeedback: String? = null,
) {
    val effectiveUnitPrice: Long
        get() = product.price + groups.asSequence()
            .filter { it.isActive }
            .flatMap { it.options.asSequence() }
            .filter { it.isActive && it.id in selectedOptionIds }
            .sumOf { it.priceDelta }

    private val unsupportedRequiredMultiple: Boolean
        get() = groups.any { it.isActive && it.required && it.selectionType == SelectionMode.MULTIPLE }

    val canAdd: Boolean
        get() {
            val hasNegativeDelta = groups.asSequence()
                .filter { it.isActive }
                .flatMap { it.options.asSequence() }
                .filter { it.isActive && it.id in selectedOptionIds }
                .any { it.priceDelta < 0 }
            return product.isActive &&
                product.modifierMetadataLoaded &&
                !unsupportedRequiredMultiple &&
                !hasNegativeDelta &&
                groups.filter { it.isActive && it.required }.all { group ->
                    group.options.any { it.isActive } && selectedFor(group).size == 1
                } &&
                selectedOptionIds.all { id -> groups.any { group ->
                    group.isActive && group.options.any { it.isActive && it.id == id }
                } }
        }

    fun groupFeedback(groupId: String): String? {
        val group = groups.firstOrNull { it.id == groupId && it.isActive } ?: return null
        if (group.required && group.selectionType == SelectionMode.MULTIPLE) return "Konfigurasi opsi tidak didukung"
        if (group.required && group.options.none { it.isActive }) return "Pilih 1 opsi"
        if (group.required && selectedFor(group).isEmpty()) return "Wajib dipilih"
        return null
    }

    fun toggleOption(optionId: String): ModifierSelectionState {
        val group = groups.firstOrNull { candidate ->
            candidate.isActive && candidate.options.any { it.id == optionId && it.isActive }
        } ?: return this
        val updated = selectedOptionIds.toMutableSet()
        if (optionId in updated) {
            updated.remove(optionId)
        } else if (group.selectionType == SelectionMode.SINGLE) {
            updated.removeAll(group.options.map { it.id }.toSet())
            updated.add(optionId)
        } else {
            updated.add(optionId)
        }
        return copy(selectedOptionIds = updated)
    }

    fun updateNote(value: String): ModifierSelectionState {
        val trimmed = value.trim()
        val normalized = if (trimmed.isEmpty()) null else trimmed
        return if (normalized != null && normalized.length > 255) {
            copy(noteFeedback = "Catatan maksimal 255 karakter")
        } else {
            copy(note = normalized, noteFeedback = null)
        }
    }

    fun selectedSnapshots(): List<CartModifierSelectionSnapshot> = groups
        .filter { it.isActive }
        .flatMap { group -> group.options.filter { it.isActive && it.id in selectedOptionIds }.map { option ->
            CartModifierSelectionSnapshot(option.id, group.id, group.name, option.name, option.priceDelta)
        } }

    private fun selectedFor(group: ModifierGroup): Set<String> =
        group.options.asSequence().filter { it.isActive && it.id in selectedOptionIds }.map { it.id }.toSet()

    companion object {
        fun create(product: Product): ModifierSelectionState = ModifierSelectionState(
            product = product,
            groups = product.modifierGroups
                .filter { it.isActive }
                .sortedBy { it.displayOrder }
                .map { group ->
                    group.copy(options = group.options.sortedBy { it.displayOrder })
                },
            selectedOptionIds = emptySet(),
            note = null,
        )
    }
}
