package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName
import com.kasirkita.pos.domain.model.CurrentUser

data class CurrentUserResponse(
    val id: String,
    val name: String,
    val role: String,
    @SerializedName("tenant_id")
    val tenantId: String,
    @SerializedName("outlet_id")
    val outletId: String?,
)

fun CurrentUserResponse.toDomain(): CurrentUser = CurrentUser(
    id = id,
    name = name,
    role = role.toAuthUserRole(),
    tenantId = tenantId,
    outletId = outletId,
)
