package com.kasirkita.pos.domain.model

data class AuthSession(
    val userId: String,
    val userName: String,
    val tenantId: String,
    val role: UserRole,
    val outletId: String?,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val deviceId: String,
)
