package com.kasirkita.pos.domain.model

data class Cart(
    val items: List<CartItem> = emptyList(),
) {
    fun totalAmount(): Long = items.sumOf(CartItem::subtotal)

    fun totalItems(): Int = items.sumOf(CartItem::quantity)
}
