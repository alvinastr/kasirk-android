package com.kasirkita.pos.domain.model

data class Outlet(
    val id: String,
    val tenantId: String,
    val name: String,
    val address: String?,
    val isActive: Boolean,
    val createdAt: String,
)
