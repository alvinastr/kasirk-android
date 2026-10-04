package com.kasirkita.pos.domain.model

data class ReceiptItem(
    val id: String,
    val productId: String,
    val productName: String,
    val sku: String,
    val quantity: Int,
    val unitPrice: Long,
    val subtotal: Long,
    val productNameSnapshot: String? = null,
    val skuSnapshot: String? = null,
    val basePriceSnapshot: Long? = null,
    val effectivePriceSnapshot: Long? = null,
    val note: String? = null,
    val modifierSnapshots: List<ModifierSnapshot> = emptyList(),
)
