package com.kasirkita.pos.domain.model

data class ProductModifierAssignment(
    val productId: String,
    val groupId: String,
    val required: Boolean,
    val selectionMode: SelectionMode,
    val displayOrder: Int
)

enum class SelectionMode {
    SINGLE,
    MULTIPLE
}
