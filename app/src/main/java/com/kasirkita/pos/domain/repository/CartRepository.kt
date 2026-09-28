package com.kasirkita.pos.domain.repository

import com.kasirkita.pos.domain.model.Cart
import com.kasirkita.pos.domain.model.Product
import kotlinx.coroutines.flow.StateFlow

enum class CartUpdateResult {
    UPDATED,
    STOCK_LIMIT_REACHED,
}

interface CartRepository {
    fun addProduct(
        product: Product,
        availableStock: Int? = null,
    ): CartUpdateResult

    fun removeProduct(productId: String)

    fun updateQuantity(productId: String, quantity: Int): CartUpdateResult

    fun getCart(): StateFlow<Cart>

    fun clearCart()
}
