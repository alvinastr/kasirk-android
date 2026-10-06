package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName

data class ProductModifierAssignmentResponse(
    @SerializedName("product_id")
    val productId: String,

    @SerializedName("modifier_group_id")
    val modifierGroupId: String,

    @SerializedName("required")
    val required: Boolean,

    @SerializedName("selection_type")
    val selectionType: String,

    @SerializedName("display_order")
    val displayOrder: Int
)