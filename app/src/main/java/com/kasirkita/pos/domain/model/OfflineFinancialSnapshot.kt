package com.kasirkita.pos.domain.model

data class OfflineFinancialSnapshot(
    val subtotal: Long,
    val discount: Long,
    val tax: Long,
    val total: Long,
    val paymentAmount: Long,
)
