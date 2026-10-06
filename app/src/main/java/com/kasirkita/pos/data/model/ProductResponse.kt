package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName

data class ProductResponse(
    val id: String,
    @SerializedName("tenant_id")
    val tenantId: String,
    @SerializedName("category_id")
    val categoryId: String?,
    val name: String,
    val sku: String,
    val price: Long,
    val cost: Long,
    @SerializedName("minimum_stock")
    val minimumStock: Int,
    @SerializedName("track_stock")
    val trackStock: Boolean,
    @SerializedName("is_active")
    val isActive: Boolean,
    @SerializedName("created_at")
    val createdAt: String,
    val stock: Int? = null,
    val category: CategoryResponse? = null,
    @SerializedName("modifier_groups")
    val modifierGroups: List<ProductModifierGroupResponse>? = null
)

data class ProductModifierGroupResponse(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("required")
    val required: Boolean,
    @SerializedName("selection_type")
    val selectionType: String,
    @SerializedName("display_order")
    val displayOrder: Int,
    @SerializedName("options")
    val options: List<ModifierOptionResponse>
)
