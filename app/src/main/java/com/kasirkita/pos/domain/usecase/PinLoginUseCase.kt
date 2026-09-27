package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.AuthTokens
import com.kasirkita.pos.domain.repository.AuthV2Repository
import javax.inject.Inject

class PinLoginUseCase @Inject constructor(
    private val authV2Repository: AuthV2Repository,
) {
    suspend operator fun invoke(
        tenantId: String,
        userId: String,
        pin: String,
        deviceId: String,
        deviceName: String? = null,
    ): Result<AuthTokens> = authV2Repository.pinLogin(
        tenantId = tenantId,
        userId = userId,
        pin = pin,
        deviceId = deviceId,
        deviceName = deviceName,
    )
}
