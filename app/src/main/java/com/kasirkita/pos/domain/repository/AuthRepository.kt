package com.kasirkita.pos.domain.repository

import com.kasirkita.pos.domain.model.UserSession

interface AuthRepository {
    suspend fun login(
        email: String,
        password: String,
        tenantId: String,
    ): Result<UserSession>
}
