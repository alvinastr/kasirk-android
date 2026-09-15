package com.kasirkita.pos.data.repository

import com.kasirkita.pos.domain.model.Cart
import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.repository.CartRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CartRepositoryImpl @Inject constructor() : CartRepository {

    private val cart = MutableStateFlow(Cart())

    override fun addProduct(product: Product) {
        cart.update { currentCart ->
            val existingItem = currentCart.items.firstOrNull {
                it.productId == product.id
            }

            if (existingItem == null) {
                currentCart.copy(
                    items = currentCart.items + CartItem(
                        productId = product.id,
                        name = product.name,
                        sku = product.sku,
                        price = product.price,
                        quantity = 1,
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
                            )
                        } else {
                            item
                        }
                    },
                )
            }
        }
    }

    override fun removeProduct(productId: String) {
        cart.update { currentCart ->
            currentCart.copy(
                items = currentCart.items.filterNot { it.productId == productId },
            )
        }
    }

    override fun updateQuantity(productId: String, quantity: Int) {
        if (quantity <= 0) {
            removeProduct(productId)
            return
        }

        cart.update { currentCart ->
            currentCart.copy(
                items = currentCart.items.map { item ->
                    if (item.productId == productId) {
                        item.copy(quantity = quantity)
                    } else {
                        item
                    }
                },
            )
        }
    }

    override fun getCart(): StateFlow<Cart> = cart.asStateFlow()

    override fun clearCart() {
        cart.value = Cart()
    }
}
