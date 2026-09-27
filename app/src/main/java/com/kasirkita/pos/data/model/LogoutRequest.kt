package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName

data class LogoutRequest(
    @SerializedName("refresh_token")
    val refreshToken: String,
)
