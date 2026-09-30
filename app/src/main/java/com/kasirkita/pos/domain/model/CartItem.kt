package com.kasirkita.pos.domain.model

data class CartItem(
    val productId: String,
    val name: String,
    val sku: String,
    val price: Long,
    val quantity: Int,
    val trackStock: Boolean = false,
    val availableStock: Int? = null,
) {
    fun subtotal(): Long = price * quantity.toLong()

    fun canIncreaseQuantity(): Boolean =
        !trackStock || availableStock == null || quantity < availableStock
}
