package com.kasirkita.pos.domain.model

data class ModifierGroup(
    val id: String,
    val tenantId: String,
    val name: String,
    val isActive: Boolean,
    val displayOrder: Int,
    val required: Boolean = false,
    val selectionType: SelectionMode = SelectionMode.SINGLE,
    val options: List<ModifierOption> = emptyList()
)
