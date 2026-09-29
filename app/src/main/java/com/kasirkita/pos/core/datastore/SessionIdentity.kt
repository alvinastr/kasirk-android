package com.kasirkita.pos.core.datastore

import com.kasirkita.pos.domain.model.AuthSession

data class SessionIdentity(
    val tenantId: String,
    val userId: String,
)

fun AuthSession.toSessionIdentity(): SessionIdentity = SessionIdentity(
    tenantId = tenantId,
    userId = userId,
)
