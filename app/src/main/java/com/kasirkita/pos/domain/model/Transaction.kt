package com.kasirkita.pos.domain.model

data class Transaction(
    val id: String,
    val clientTransactionId: String,
    val outletId: String,
    val userId: String,
    val customerId: String?,
    val status: String,
    val subtotal: Long,
    val discount: Long,
    val tax: Long,
    val total: Long,
    val items: List<TransactionItem>,
    val payments: List<Payment>,
    val change: Long?,
    val createdAt: String,
)

data class TransactionItem(
    val id: String,
    val productId: String,
    val quantity: Int,
    val unitPrice: Long,
    val subtotal: Long,
)
