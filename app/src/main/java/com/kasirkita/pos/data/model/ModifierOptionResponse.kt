package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName
import com.kasirkita.pos.domain.model.ModifierOption

data class ModifierOptionResponse(
    @SerializedName("id")
    val id: String,
    @SerializedName("modifier_group_id")
    val modifierGroupId: String?,
    @SerializedName("name")
    val name: String,
    @SerializedName("price_delta")
    val priceDelta: Long,
    @SerializedName("is_active")
    val isActive: Boolean,
    @SerializedName("display_order")
    val displayOrder: Int
)

fun ModifierOptionResponse.toDomain(parentGroupId: String): ModifierOption = ModifierOption(
    id = id,
    groupId = modifierGroupId ?: parentGroupId,
    name = name,
    priceDelta = priceDelta,
    isActive = isActive,
    displayOrder = displayOrder
)
