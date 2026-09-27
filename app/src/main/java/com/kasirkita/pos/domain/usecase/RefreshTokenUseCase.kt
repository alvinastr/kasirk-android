package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.AuthTokens
import com.kasirkita.pos.domain.repository.AuthV2Repository
import javax.inject.Inject

class RefreshTokenUseCase @Inject constructor(
    private val authV2Repository: AuthV2Repository,
) {
    suspend operator fun invoke(refreshToken: String): Result<AuthTokens> =
        authV2Repository.refreshToken(refreshToken)
}
