package com.kasirkita.pos.domain.model

data class Payment(
    val id: String,
    val method: String,
    val status: String,
    val amount: Long,
    val paidAt: String?,
)
