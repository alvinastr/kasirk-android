package com.kasirkita.pos.domain.model

data class Shift(
    val id: String,
    val outletId: String,
    val userId: String,
    val openingCash: Long,
    val closingCash: Long?,
    val expectedCash: Long?,
    val difference: Long?,
    val status: String,
    val openedAt: String,
    val closedAt: String?,
)
