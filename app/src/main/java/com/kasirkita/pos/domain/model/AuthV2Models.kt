package com.kasirkita.pos.domain.model

data class ResolvedStore(
    val tenant: StoreTenant,
    val users: List<StoreUser>,
)

data class StoreTenant(
    val id: String,
    val name: String,
)

data class StoreUser(
    val id: String,
    val name: String,
    val role: UserRole,
    val outletId: String?,
)

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
)

data class CurrentUser(
    val id: String,
    val name: String,
    val role: UserRole,
    val tenantId: String,
    val outletId: String?,
)
