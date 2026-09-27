package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName
import com.kasirkita.pos.domain.model.AuthTokens

data class AuthTokenResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("refresh_token")
    val refreshToken: String,
    @SerializedName("expires_in")
    val expiresIn: Long,
)

fun AuthTokenResponse.toDomain(): AuthTokens = AuthTokens(
    accessToken = accessToken,
    refreshToken = refreshToken,
    expiresInSeconds = expiresIn,
)
