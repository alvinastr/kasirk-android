package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName

data class CreateModifierGroupRequest(
    @SerializedName("name")
    val name: String,

    @SerializedName("is_active")
    val isActive: Boolean = true,

    @SerializedName("display_order")
    val displayOrder: Int = 0
)