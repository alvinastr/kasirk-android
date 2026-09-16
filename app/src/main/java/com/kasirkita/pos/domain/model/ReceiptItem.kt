package com.kasirkita.pos.domain.model

data class ReceiptItem(
    val id: String,
    val productId: String,
    val productName: String,
    val sku: String,
    val quantity: Int,
    val unitPrice: Long,
    val subtotal: Long,
)
