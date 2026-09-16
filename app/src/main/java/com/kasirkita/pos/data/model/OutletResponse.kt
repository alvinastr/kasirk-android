package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName
import com.kasirkita.pos.domain.model.Outlet

data class OutletResponse(
    val id: String,
    @SerializedName("tenant_id")
    val tenantId: String,
    val name: String,
    val address: String?,
    @SerializedName("is_active")
    val isActive: Boolean,
    @SerializedName("created_at")
    val createdAt: String,
)

fun OutletResponse.toDomain(): Outlet = Outlet(
    id = id,
    tenantId = tenantId,
    name = name,
    address = address,
    isActive = isActive,
    createdAt = createdAt,
)
