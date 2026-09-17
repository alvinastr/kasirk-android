package com.kasirkita.pos.presentation.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.repository.CartRepository
import com.kasirkita.pos.domain.repository.OutletRepository
import com.kasirkita.pos.domain.repository.ShiftRepository
import com.kasirkita.pos.domain.usecase.CreateTransactionUseCase
import com.kasirkita.pos.domain.usecase.QueueOfflineTransactionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val createTransaction: CreateTransactionUseCase,
    private val queueOfflineTransaction: QueueOfflineTransactionUseCase,
    private val cartRepository: CartRepository,
    outletRepository: OutletRepository,
    shiftRepository: ShiftRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CheckoutState())
    val state: StateFlow<CheckoutState> = _state.asStateFlow()
    private var activeClientTransactionId: String? = null

    init {
        viewModelScope.launch {
            combine(
                cartRepository.getCart(),
                outletRepository.selectedOutlet,
                shiftRepository.currentShift,
            ) { cart, outlet, shift -> Triple(cart, outlet, shift) }
                .collect { (cart, outlet, shift) ->
                    _state.update { current ->
                        current.copy(
                            cart = cart,
                            selectedOutlet = outlet,
                            currentShift = shift,
                        )
                    }
                }
        }
    }

    fun checkout(paymentAmount: Long) {
        val snapshot = _state.value
        if (
            snapshot.isLoading ||
            snapshot.transaction != null ||
            snapshot.offlineQueuedClientTransactionId != null
        ) {
            return
        }

        val outlet = snapshot.selectedOutlet
        val shift = snapshot.currentShift
        val error = when {
            snapshot.cart.items.isEmpty() -> "Cart masih kosong"
            outlet == null -> "Outlet belum dipilih"
            shift == null || !shift.status.equals(OPEN_STATUS, ignoreCase = true) ->
                "Tidak ada shift aktif"
            shift.outletId != outlet.id -> "Shift aktif tidak sesuai dengan outlet yang dipilih"
            paymentAmount < snapshot.cart.totalAmount() ->
                "Jumlah pembayaran kurang dari total belanja"
            else -> null
        }

        if (error != null) {
            _state.update { it.copy(errorMessage = error) }
            return
        }
        val outletId = outlet?.id ?: return
        val clientTransactionId = activeClientTransactionId
            ?: UUID.randomUUID().toString().also { generatedId ->
                activeClientTransactionId = generatedId
            }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val onlineResult = createTransaction(
                clientTransactionId = clientTransactionId,
                outletId = outletId,
                items = snapshot.cart.items,
                paymentAmount = paymentAmount,
            )

            val transaction = onlineResult.getOrNull()
            if (transaction != null) {
                cartRepository.clearCart()
                _state.update {
                    it.copy(
                        isLoading = false,
                        transaction = transaction,
                        errorMessage = null,
                    )
                }
                return@launch
            }

            val throwable = onlineResult.exceptionOrNull()
            if (throwable !is IOException) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable?.message ?: "Checkout gagal",
                    )
                }
                return@launch
            }

            queueOfflineTransaction(
                clientTransactionId = clientTransactionId,
                outletId = outletId,
                customerId = null,
                items = snapshot.cart.items,
                paymentAmount = paymentAmount,
            ).fold(
                onSuccess = {
                    cartRepository.clearCart()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            offlineQueuedClientTransactionId = clientTransactionId,
                            errorMessage = null,
                        )
                    }
                },
                onFailure = { storageError ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = buildString {
                                append("Koneksi gagal dan transaksi tidak dapat disimpan offline")
                                storageError.message?.let { message -> append(": $message") }
                            },
                        )
                    }
                },
            )
        }
    }

    private companion object {
        const val OPEN_STATUS = "OPEN"
    }
}
