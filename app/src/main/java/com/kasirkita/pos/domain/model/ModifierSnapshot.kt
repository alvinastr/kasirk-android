package com.kasirkita.pos.domain.model

data class ModifierSnapshot(
    val id: String,
    val modifierGroupId: String?,
    val modifierOptionId: String?,
    val groupName: String,
    val optionName: String,
    val priceDelta: Long
)
