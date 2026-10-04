package com.kasirkita.pos.domain.model

data class ModifierOption(
    val id: String,
    val groupId: String,
    val name: String,
    val priceDelta: Long,
    val isActive: Boolean,
    val displayOrder: Int
)
