package com.kasirkita.pos.data.repository

import com.kasirkita.pos.data.api.AuthV2Api
import com.kasirkita.pos.data.model.LogoutRequest
import com.kasirkita.pos.data.model.PinLoginRequest
import com.kasirkita.pos.data.model.RefreshTokenRequest
import com.kasirkita.pos.data.model.StoreResolveRequest
import com.kasirkita.pos.data.model.toDomain
import com.kasirkita.pos.domain.model.AuthTokens
import com.kasirkita.pos.domain.model.CurrentUser
import com.kasirkita.pos.domain.model.ResolvedStore
import com.kasirkita.pos.domain.repository.AuthV2Repository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthV2RepositoryImpl @Inject constructor(
    private val authV2Api: AuthV2Api,
) : AuthV2Repository {

    override suspend fun resolveStore(storeCode: String): Result<ResolvedStore> = runCatching {
        authV2Api.resolveStore(StoreResolveRequest(storeCode)).toDomain()
    }

    override suspend fun pinLogin(
        tenantId: String,
        userId: String,
        pin: String,
        deviceId: String,
        deviceName: String?,
    ): Result<AuthTokens> = runCatching {
        authV2Api.pinLogin(
            PinLoginRequest(
                tenantId = tenantId,
                userId = userId,
                pin = pin,
                deviceId = deviceId,
                deviceName = deviceName,
            ),
        ).toDomain()
    }

    override suspend fun refreshToken(refreshToken: String): Result<AuthTokens> = runCatching {
        authV2Api.refreshToken(RefreshTokenRequest(refreshToken)).toDomain()
    }

    override suspend fun getCurrentUser(): Result<CurrentUser> = runCatching {
        authV2Api.getCurrentUser().toDomain()
    }

    override suspend fun logout(refreshToken: String): Result<Unit> = runCatching {
        authV2Api.logout(LogoutRequest(refreshToken))
    }
}
