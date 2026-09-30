package com.kasirkita.pos.data.repository

import com.kasirkita.pos.domain.model.Cart
import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.Product
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

    @Synchronized
    override fun addProduct(
        product: Product,
        availableStock: Int?,
    ): CartUpdateResult {
        val currentCart = cart.value
        val existingItem = currentCart.items.firstOrNull { item ->
            item.productId == product.id
        }
        val stockLimit = availableStock?.coerceAtLeast(0)

        if (
            product.trackStock &&
            stockLimit != null &&
            (existingItem?.quantity ?: 0) >= stockLimit
        ) {
            if (existingItem != null) {
                cart.value = currentCart.copy(
                    items = currentCart.items.map { item ->
                        if (item.productId == product.id) {
                            item.copy(availableStock = stockLimit)
                        } else {
                            item
                        }
                    },
                )
            }
            return CartUpdateResult.STOCK_LIMIT_REACHED
        }

        cart.value = if (existingItem == null) {
            currentCart.copy(
                items = currentCart.items + CartItem(
                    productId = product.id,
                    name = product.name,
                    sku = product.sku,
                    price = product.price,
                    quantity = 1,
                    trackStock = product.trackStock,
                    availableStock = stockLimit,
                ),
            )
        } else {
            currentCart.copy(
                items = currentCart.items.map { item ->
                    if (item.productId == product.id) {
                        item.copy(
                            name = product.name,
                            sku = product.sku,
                            price = product.price,
                            quantity = item.quantity + 1,
                            trackStock = product.trackStock,
                            availableStock = stockLimit,
                        )
                    } else {
                        item
                    }
                },
            )
        }
        return CartUpdateResult.UPDATED
    }

    @Synchronized
    override fun removeProduct(productId: String) {
        cart.value = cart.value.copy(
            items = cart.value.items.filterNot { it.productId == productId },
        )
    }

    @Synchronized
    override fun updateQuantity(productId: String, quantity: Int): CartUpdateResult {
        if (quantity <= 0) {
            removeProduct(productId)
            return CartUpdateResult.UPDATED
        }

        val currentCart = cart.value
        val existingItem = currentCart.items.firstOrNull { item ->
            item.productId == productId
        } ?: return CartUpdateResult.UPDATED

        if (
            existingItem.trackStock &&
            existingItem.availableStock != null &&
            quantity > existingItem.availableStock
        ) {
            return CartUpdateResult.STOCK_LIMIT_REACHED
        }

        cart.value = currentCart.copy(
            items = currentCart.items.map { item ->
                if (item.productId == productId) {
                    item.copy(quantity = quantity)
                } else {
                    item
                }
            },
        )
        return CartUpdateResult.UPDATED
    }

    override fun getCart(): StateFlow<Cart> = cart.asStateFlow()

    @Synchronized
    override fun clearCart() {
        cart.value = Cart()
    }
}
