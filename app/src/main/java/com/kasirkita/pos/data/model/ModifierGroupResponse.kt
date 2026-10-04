package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName
import com.kasirkita.pos.domain.model.ModifierGroup
import com.kasirkita.pos.domain.model.SelectionMode

data class ModifierGroupResponse(
    @SerializedName("id")
    val id: String,
    @SerializedName("tenant_id")
    val tenantId: String?,
    @SerializedName("name")
    val name: String,
    @SerializedName("is_active")
    val isActive: Boolean,
    @SerializedName("display_order")
    val displayOrder: Int,
    @SerializedName("required")
    val required: Boolean,
    @SerializedName("selection_type")
    val selectionType: String,
    @SerializedName("options")
    val options: List<ModifierOptionResponse>
)

fun ModifierGroupResponse.toDomain(parentTenantId: String? = null): ModifierGroup = ModifierGroup(
    id = id,
    tenantId = requireNotNull(tenantId ?: parentTenantId) {
        "Modifier group tenant ID is missing"
    },
    name = name,
    isActive = isActive,
    displayOrder = displayOrder,
    required = required,
    selectionType = SelectionMode.valueOf(selectionType),
    options = options.map { it.toDomain(id) }
)
