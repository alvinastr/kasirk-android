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
    @SerializedName("is_active")
    val isActive: Boolean,
    @SerializedName("created_at")
    val createdAt: String,
)
