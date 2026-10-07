package com.kasirkita.pos.domain.model

data class HeldOrderCartIdentity(
    val heldOrderId: String,
    val expectedVersion: Int,
    val label: String?,
)
