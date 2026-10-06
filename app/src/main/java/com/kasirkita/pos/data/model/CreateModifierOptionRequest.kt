package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName

data class CreateModifierOptionRequest(
    @SerializedName("name")
    val name: String,

    @SerializedName("price_delta")
    val priceDelta: Long = 0,

    @SerializedName("is_active")
    val isActive: Boolean = true,

    @SerializedName("display_order")
    val displayOrder: Int = 0
)