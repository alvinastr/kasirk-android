package com.kasirkita.pos.domain.repository

import com.kasirkita.pos.domain.model.Cart
import com.kasirkita.pos.domain.model.CartModifierSelectionSnapshot
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

    fun addConfiguredProduct(
        product: Product,
        selectedModifiers: List<CartModifierSelectionSnapshot>,
        note: String?,
        availableStock: Int? = null,
    ): CartUpdateResult

    fun removeProduct(lineKey: String)

    fun updateQuantity(lineKey: String, quantity: Int): CartUpdateResult

    fun getCart(): StateFlow<Cart>

    fun clearCart()
}
