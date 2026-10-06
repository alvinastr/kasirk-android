package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName

data class UpdateModifierOptionRequest(
    @SerializedName("name")
    val name: String? = null,

    @SerializedName("price_delta")
    val priceDelta: Long? = null,

    @SerializedName("is_active")
    val isActive: Boolean? = null,

    @SerializedName("display_order")
    val displayOrder: Int? = null
)