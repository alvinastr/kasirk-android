package com.kasirkita.pos.domain.model

data class UserSession(
    val accessToken: String,
    val tenantId: String,
    val userId: String,
    val role: UserRole,
)

enum class UserRole {
    OWNER,
    ADMIN,
    CASHIER,
}
