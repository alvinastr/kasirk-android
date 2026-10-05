package com.kasirkita.pos.data.repository

import com.kasirkita.pos.domain.model.Cart
import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.CartLineKey
import com.kasirkita.pos.domain.model.CartModifierSelectionSnapshot
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.model.normalizeItemNote
import com.kasirkita.pos.domain.repository.CartRepository
import com.kasirkita.pos.domain.repository.CartUpdateResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CartRepositoryImpl @Inject constructor() : CartRepository {

    private val cart = MutableStateFlow(Cart())

    override fun addProduct(product: Product, availableStock: Int?): CartUpdateResult =
        addConfiguredProduct(product, emptyList(), null, availableStock)

    @Synchronized
    override fun addConfiguredProduct(
        product: Product,
        selectedModifiers: List<CartModifierSelectionSnapshot>,
        note: String?,
        availableStock: Int?,
    ): CartUpdateResult {
        val normalizedNote = normalizeItemNote(note)
        val normalizedSelections = selectedModifiers
            .distinctBy { it.optionId }
            .sortedBy { it.optionId }
        val key = CartLineKey.from(product.id, normalizedSelections.map { it.optionId }, normalizedNote)
        val currentCart = cart.value
        val existingItem = currentCart.items.firstOrNull { it.lineKey == key }
        val stockLimit = availableStock?.coerceAtLeast(0)

        if (product.trackStock && stockLimit != null && (existingItem?.quantity ?: 0) >= stockLimit) {
            if (existingItem != null) {
                cart.value = currentCart.copy(items = currentCart.items.map { item ->
                    if (item.lineKey == key) item.copy(availableStock = stockLimit) else item
                })
            }
            return CartUpdateResult.STOCK_LIMIT_REACHED
        }

        cart.value = if (existingItem == null) {
            currentCart.copy(items = currentCart.items + CartItem(
                lineKey = key,
                productId = product.id,
                name = product.name,
                sku = product.sku,
                basePrice = product.price,
                quantity = 1,
                modifierSelections = normalizedSelections,
                note = normalizedNote,
                trackStock = product.trackStock,
                availableStock = stockLimit,
            ))
        } else {
            currentCart.copy(items = currentCart.items.map { item ->
                if (item.lineKey == key) item.copy(
                    name = product.name,
                    sku = product.sku,
                    basePrice = product.price,
                    quantity = item.quantity + 1,
                    modifierSelections = normalizedSelections,
                    trackStock = product.trackStock,
                    availableStock = stockLimit,
                ) else item
            })
        }
        return CartUpdateResult.UPDATED
    }

    @Synchronized
    override fun removeProduct(lineKey: String) {
        cart.value = cart.value.copy(items = cart.value.items.filterNot { it.lineKey.value == lineKey })
    }

    @Synchronized
    override fun updateQuantity(lineKey: String, quantity: Int): CartUpdateResult {
        val currentCart = cart.value
        val existingItem = currentCart.items.firstOrNull { it.lineKey.value == lineKey }
            ?: return CartUpdateResult.UPDATED
        if (quantity <= 0) {
            removeProduct(lineKey)
            return CartUpdateResult.UPDATED
        }
        if (existingItem.trackStock && existingItem.availableStock != null && quantity > existingItem.availableStock) {
            return CartUpdateResult.STOCK_LIMIT_REACHED
        }
        cart.value = currentCart.copy(items = currentCart.items.map { item ->
            if (item.lineKey.value == lineKey) item.copy(quantity = quantity) else item
        })
        return CartUpdateResult.UPDATED
    }

    override fun getCart(): StateFlow<Cart> = cart.asStateFlow()

    @Synchronized
    override fun clearCart() {
        cart.value = Cart()
    }
}
