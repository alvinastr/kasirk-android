package com.kasirkita.pos.presentation.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.repository.CartRepository
import com.kasirkita.pos.domain.repository.CartUpdateResult
import com.kasirkita.pos.domain.usecase.AddToCartUseCase
import com.kasirkita.pos.domain.usecase.RemoveFromCartUseCase
import com.kasirkita.pos.domain.usecase.UpdateCartQuantityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CartViewModel @Inject constructor(
    private val cartRepository: CartRepository,
    private val addToCart: AddToCartUseCase,
    private val removeFromCart: RemoveFromCartUseCase,
    private val updateCartQuantity: UpdateCartQuantityUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(CartState())
    val state: StateFlow<CartState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            cartRepository.getCart().map { CartState(it) }.collect { newState ->
                _state.update { newState }
            }
        }
    }

    fun increaseQuantity(lineKey: String) {
        val item = _state.value.cart.items.find { it.lineKey.value == lineKey } ?: return
        handleUpdate(updateCartQuantity(lineKey, item.quantity + 1))
    }

    fun decreaseQuantity(lineKey: String) {
        val item = _state.value.cart.items.find { it.lineKey.value == lineKey } ?: return
        handleUpdate(updateCartQuantity(lineKey, item.quantity - 1))
    }

    fun removeProduct(lineKey: String) {
        removeFromCart(lineKey)
    }

    private fun handleUpdate(result: CartUpdateResult) {
        errorMessage.value = when (result) {
            CartUpdateResult.UPDATED -> null
            CartUpdateResult.STOCK_LIMIT_REACHED ->
                "Jumlah di cart sudah mencapai stok yang tersedia."
        }
    }

    companion object {
        private val errorMessage = MutableStateFlow<String?>(null)
    }
}
