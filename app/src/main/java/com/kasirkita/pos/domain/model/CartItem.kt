package com.kasirkita.pos.domain.model

data class CartItem(
    val productId: String,
    val name: String,
    val sku: String,
    val price: Long,
    val quantity: Int,
) {
    fun subtotal(): Long = price * quantity.toLong()
}
