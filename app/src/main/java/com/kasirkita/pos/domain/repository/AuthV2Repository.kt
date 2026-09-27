package com.kasirkita.pos.domain.repository

import com.kasirkita.pos.domain.model.AuthTokens
import com.kasirkita.pos.domain.model.CurrentUser
import com.kasirkita.pos.domain.model.ResolvedStore

interface AuthV2Repository {
    suspend fun resolveStore(storeCode: String): Result<ResolvedStore>

    suspend fun pinLogin(
        tenantId: String,
        userId: String,
        pin: String,
        deviceId: String,
        deviceName: String? = null,
    ): Result<AuthTokens>

    suspend fun refreshToken(refreshToken: String): Result<AuthTokens>

    suspend fun getCurrentUser(): Result<CurrentUser>

    suspend fun logout(refreshToken: String): Result<Unit>
}
