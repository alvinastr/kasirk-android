package com.kasirkita.pos.data.model

data class PaymentRequest(
    val method: String,
    val amount: Long? = null,
    @com.google.gson.annotations.SerializedName("amount_received")
    val amountReceived: Long? = null,
)
