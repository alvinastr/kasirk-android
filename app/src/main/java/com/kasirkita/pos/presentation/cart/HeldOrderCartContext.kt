package com.kasirkita.pos.presentation.cart

import androidx.lifecycle.SavedStateHandle

/**
 * Lightweight Held Order editing identity persisted across process death.
 *
 * ONLY the editing identity is persisted:
 *  - [heldOrderId]
 *  - [expectedVersion]
 *  - [label]
 *  - the editing flag (derived from [heldOrderId] being present)
 *
 * Held Order *contents* (items, prices, modifiers, notes) are deliberately never
 * serialized here. On process recreation the authoritative Held Order detail must
 * be refetched from the backend and the working cart rebuilt only after that
 * succeeds. This is what guarantees that a cart associated with a Held Order can
 * never lose its identity and then silently become eligible for normal checkout:
 * the identity survives, so the "editing a Held Order" state is re-established
 * rather than degraded into a normal cart.
 *
 * A cart is only considered attached to a Held Order when [heldOrderId] is
 * non-blank, so a partially written bundle can never present as an attached cart.
 */
internal class HeldOrderCartContext(
    private val savedStateHandle: SavedStateHandle,
) {
    var heldOrderId: String?
        get() = savedStateHandle.get<String>(KEY_HELD_ORDER_ID)?.takeIf { it.isNotBlank() }
        private set(value) {
            if (value.isNullOrBlank()) {
                savedStateHandle.remove<String>(KEY_HELD_ORDER_ID)
            } else {
                savedStateHandle[KEY_HELD_ORDER_ID] = value
            }
        }

    var expectedVersion: Int?
        get() = savedStateHandle.get<Int>(KEY_EXPECTED_VERSION)
        private set(value) {
            if (value == null) {
                savedStateHandle.remove<Int>(KEY_EXPECTED_VERSION)
            } else {
                savedStateHandle[KEY_EXPECTED_VERSION] = value
            }
        }

    var label: String?
        get() = savedStateHandle.get<String>(KEY_LABEL)
        private set(value) {
            if (value.isNullOrBlank()) {
                savedStateHandle.remove<String>(KEY_LABEL)
            } else {
                savedStateHandle[KEY_LABEL] = value
            }
        }

    val isEditing: Boolean
        get() = heldOrderId != null

    fun attach(heldOrderId: String, expectedVersion: Int, label: String?) {
        this.heldOrderId = heldOrderId
        this.expectedVersion = expectedVersion
        this.label = label
    }

    /** Clears the Held Order identity. Never touches cart contents. */
    fun clear() {
        heldOrderId = null
        expectedVersion = null
        label = null
    }

    /** Only identity keys are ever persisted; contents are never stored. */
    fun persistedKeys(): Set<String> = setOf(KEY_HELD_ORDER_ID, KEY_EXPECTED_VERSION, KEY_LABEL)

    internal companion object {
        const val KEY_HELD_ORDER_ID = "heldOrderId"
        const val KEY_EXPECTED_VERSION = "heldOrderExpectedVersion"
        const val KEY_LABEL = "heldOrderLabel"
    }
}
