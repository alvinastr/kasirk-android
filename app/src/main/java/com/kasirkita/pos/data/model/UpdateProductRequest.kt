package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName

data class UpdateProductRequest(
    val name: String? = null,
    val sku: String? = null,
    @SerializedName("category_id")
    val categoryId: String? = null,
    val price: Long? = null,
    val cost: Long? = null,
    @SerializedName("minimum_stock")
    val minimumStock: Int? = null,
    @SerializedName("track_stock")
    val trackStock: Boolean? = null,
)
