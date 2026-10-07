package com.kasirkita.pos.domain.repository

import com.kasirkita.pos.domain.model.Cart
import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.CartModifierSelectionSnapshot
import com.kasirkita.pos.domain.model.HeldOrderCartIdentity
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

    /**
     * Atomically replaces the entire cart with the provided items.
     * Validates the complete candidate cart (including stock limits) BEFORE any mutation.
     * If validation fails, the current cart is left completely untouched.
     * Returns [CartUpdateResult.STOCK_LIMIT_REACHED] if any stock-tracked item exceeds available stock.
     */
    fun replaceCart(items: List<CartItem>): CartUpdateResult

    /**
     * True while the cart is attached to a saved Held Order and therefore must NOT
     * be processed by the normal checkout path (transaction creation, offline queue,
     * printing, cash drawer). Attachment is stored in memory only, next to the cart,
     * so any action-level guard can enforce this without depending on presentation state.
     */
    fun isAttachedToHeldOrder(): Boolean = false

    fun getHeldOrderIdentity(): StateFlow<HeldOrderCartIdentity?> =
        kotlinx.coroutines.flow.MutableStateFlow(null)

    fun attachHeldOrderIdentity(heldOrderId: String, expectedVersion: Int, label: String?) = Unit

    fun detachHeldOrderIdentity() = Unit

    fun getCart(): StateFlow<Cart>

    fun clearCart()
}
