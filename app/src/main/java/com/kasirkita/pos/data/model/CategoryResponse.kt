package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName
import com.kasirkita.pos.domain.model.Category

data class CategoryResponse(
    val id: String,
    @SerializedName("tenant_id")
    val tenantId: String,
    val name: String,
    @SerializedName("created_at")
    val createdAt: String,
)

fun CategoryResponse.toDomain(): Category = Category(
    id = id,
    tenantId = tenantId,
    name = name,
    createdAt = createdAt,
)
