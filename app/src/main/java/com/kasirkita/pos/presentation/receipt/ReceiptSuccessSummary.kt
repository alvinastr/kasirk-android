package com.kasirkita.pos.presentation.receipt

import com.kasirkita.pos.domain.model.Receipt

data class ReceiptSuccessSummary(
    val total: Long,
    val paymentMethod: String,
    val amountPaid: Long,
    val change: Long,
)

fun Receipt.toSuccessSummary(): ReceiptSuccessSummary = ReceiptSuccessSummary(
    total = total,
    paymentMethod = payment?.method ?: "-",
    amountPaid = payment?.amountReceived ?: payment?.amount ?: total,
    change = payment?.changeAmount ?: change ?: 0L,
)
