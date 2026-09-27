package com.kasirkita.pos.data.api

import com.kasirkita.pos.data.model.AuthTokenResponse
import com.kasirkita.pos.data.model.CurrentUserResponse
import com.kasirkita.pos.data.model.LogoutRequest
import com.kasirkita.pos.data.model.PinLoginRequest
import com.kasirkita.pos.data.model.RefreshTokenRequest
import com.kasirkita.pos.data.model.StoreResolveRequest
import com.kasirkita.pos.data.model.StoreResolveResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthV2Api {

    @POST("auth/v2/store/resolve")
    suspend fun resolveStore(
        @Body request: StoreResolveRequest,
    ): StoreResolveResponse

    @POST("auth/v2/pin/login")
    suspend fun pinLogin(
        @Body request: PinLoginRequest,
    ): AuthTokenResponse

    @POST("auth/v2/refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest,
    ): AuthTokenResponse

    @POST("auth/v2/logout")
    suspend fun logout(
        @Body request: LogoutRequest,
    )

    @GET("auth/v2/me")
    suspend fun getCurrentUser(): CurrentUserResponse
}
