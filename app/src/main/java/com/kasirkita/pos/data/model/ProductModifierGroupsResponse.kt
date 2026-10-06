package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName

data class ProductModifierGroupsResponse(
    @SerializedName("product_id")
    val productId: String,

    @SerializedName("modifier_groups")
    val modifierGroups: List<ProductModifierAssignmentResponse>
)