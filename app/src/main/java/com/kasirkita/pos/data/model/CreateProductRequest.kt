package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName

data class CreateProductRequest(
    val name: String,
    val sku: String,
    @SerializedName("category_id")
    val categoryId: String?,
    val price: Long,
    val cost: Long,
    @SerializedName("minimum_stock")
    val minimumStock: Int,
    @SerializedName("track_stock")
    val trackStock: Boolean,
)
