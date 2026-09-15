package com.kasirkita.pos.domain.repository

import com.kasirkita.pos.domain.model.Cart
import com.kasirkita.pos.domain.model.Product
import kotlinx.coroutines.flow.StateFlow

interface CartRepository {
    fun addProduct(product: Product)

    fun removeProduct(productId: String)

    fun updateQuantity(productId: String, quantity: Int)

    fun getCart(): StateFlow<Cart>

    fun clearCart()
}
