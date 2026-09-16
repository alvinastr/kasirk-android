package com.kasirkita.pos.data.model

data class PaymentRequest(
    val method: String,
    val amount: Long,
)
