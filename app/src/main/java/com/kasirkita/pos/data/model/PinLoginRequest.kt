package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName

data class PinLoginRequest(
    @SerializedName("tenant_id")
    val tenantId: String,
    @SerializedName("user_id")
    val userId: String,
    val pin: String,
    @SerializedName("device_id")
    val deviceId: String,
    @SerializedName("device_name")
    val deviceName: String? = null,
)
