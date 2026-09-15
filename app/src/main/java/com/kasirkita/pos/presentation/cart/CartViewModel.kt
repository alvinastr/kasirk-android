package com.kasirkita.pos.presentation.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.repository.CartRepository
import com.kasirkita.pos.domain.usecase.AddToCartUseCase
import com.kasirkita.pos.domain.usecase.GetCartUseCase
import com.kasirkita.pos.domain.usecase.RemoveFromCartUseCase
import com.kasirkita.pos.domain.usecase.UpdateCartQuantityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class CartViewModel @Inject constructor(
    getCartUseCase: GetCartUseCase,
    private val addToCartUseCase: AddToCartUseCase,
    private val removeFromCartUseCase: RemoveFromCartUseCase,
    private val updateCartQuantityUseCase: UpdateCartQuantityUseCase,
    private val cartRepository: CartRepository,
) : ViewModel() {

    val state: StateFlow<CartState> = getCartUseCase()
        .map(::CartState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = CartState(),
        )

    fun addProduct(product: Product) {
        addToCartUseCase(product)
    }

    fun removeProduct(productId: String) {
        removeFromCartUseCase(productId)
    }

    fun increaseQuantity(productId: String) {
        val item = state.value.cart.items.firstOrNull { it.productId == productId } ?: return
        updateCartQuantityUseCase(productId, item.quantity + 1)
    }

    fun decreaseQuantity(productId: String) {
        val item = state.value.cart.items.firstOrNull { it.productId == productId } ?: return
        updateCartQuantityUseCase(productId, item.quantity - 1)
    }

    fun clearCart() {
        cartRepository.clearCart()
    }
}
