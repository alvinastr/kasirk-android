package com.kasirkita.pos.domain.model

data class Stock(
    val id: String,
    val outletId: String,
    val productId: String,
    val quantity: Int,
    val updatedAt: String,
    val productName: String? = null,
    val productSku: String? = null,
)

enum class StockAdjustmentType {
    ADD,
    DEDUCT,
}
