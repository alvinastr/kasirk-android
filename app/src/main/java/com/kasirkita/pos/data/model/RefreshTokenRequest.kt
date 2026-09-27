package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName

data class RefreshTokenRequest(
    @SerializedName("refresh_token")
    val refreshToken: String,
)
