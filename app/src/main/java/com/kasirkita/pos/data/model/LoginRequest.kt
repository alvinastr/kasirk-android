package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val email: String,
    val password: String,
    @SerializedName("tenant_id")
    val tenantId: String,
)
