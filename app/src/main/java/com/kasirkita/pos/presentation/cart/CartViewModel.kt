package com.kasirkita.pos.presentation.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.repository.CartRepository
import com.kasirkita.pos.domain.repository.CartUpdateResult
import com.kasirkita.pos.domain.usecase.AddToCartUseCase
import com.kasirkita.pos.domain.usecase.GetCartUseCase
import com.kasirkita.pos.domain.usecase.RemoveFromCartUseCase
import com.kasirkita.pos.domain.usecase.UpdateCartQuantityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

    private val errorMessage = MutableStateFlow<String?>(null)

    val state: StateFlow<CartState> = combine(
        getCartUseCase(),
        errorMessage,
        ::CartState,
    )
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = CartState(),
        )

    fun addProduct(product: Product) {
        handleUpdate(addToCartUseCase(product))
    }

    fun removeProduct(productId: String) {
        removeFromCartUseCase(productId)
        errorMessage.value = null
    }

    fun increaseQuantity(productId: String) {
        val item = state.value.cart.items.firstOrNull { it.productId == productId } ?: return
        handleUpdate(updateCartQuantityUseCase(productId, item.quantity + 1))
    }

    fun decreaseQuantity(productId: String) {
        val item = state.value.cart.items.firstOrNull { it.productId == productId } ?: return
        updateCartQuantityUseCase(productId, item.quantity - 1)
        errorMessage.value = null
    }

    fun clearCart() {
        cartRepository.clearCart()
        errorMessage.value = null
    }

    private fun handleUpdate(result: CartUpdateResult) {
        errorMessage.value = when (result) {
            CartUpdateResult.UPDATED -> null
            CartUpdateResult.STOCK_LIMIT_REACHED ->
                "Jumlah di cart sudah mencapai stok yang tersedia."
        }
    }
}
