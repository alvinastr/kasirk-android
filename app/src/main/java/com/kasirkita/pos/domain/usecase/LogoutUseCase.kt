package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.repository.AuthV2Repository
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val authV2Repository: AuthV2Repository,
) {
    suspend operator fun invoke(refreshToken: String): Result<Unit> =
        authV2Repository.logout(refreshToken)
}
