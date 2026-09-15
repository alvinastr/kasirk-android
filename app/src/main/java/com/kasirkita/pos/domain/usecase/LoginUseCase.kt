package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.UserSession
import com.kasirkita.pos.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(
        email: String,
        password: String,
        tenantId: String,
    ): Result<UserSession> = authRepository.login(
        email = email,
        password = password,
        tenantId = tenantId,
    )
}
