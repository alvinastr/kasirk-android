package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName

data class ProductModifierAssignmentResponse(
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