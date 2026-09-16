package com.kasirkita.pos.domain.model

data class Receipt(
    val transactionId: String,
    val clientTransactionId: String,
    val status: String,
    val createdAt: String,
    val tenant: ReceiptTenant,
    val outlet: ReceiptOutlet,
    val cashier: ReceiptCashier,
    val customer: ReceiptCustomer?,
    val items: List<ReceiptItem>,
    val payment: Payment?,
    val subtotal: Long,
    val discount: Long,
    val tax: Long,
    val total: Long,
    val change: Long?,
)

data class ReceiptTenant(
    val id: String,
    val name: String,
    val address: String?,
)

data class ReceiptOutlet(
    val id: String,
    val name: String,
    val address: String?,
)

data class ReceiptCashier(
    val id: String,
    val name: String,
)

data class ReceiptCustomer(
    val id: String,
    val name: String,
    val phone: String?,
    val email: String?,
)
