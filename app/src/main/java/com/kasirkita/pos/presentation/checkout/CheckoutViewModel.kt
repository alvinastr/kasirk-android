package com.kasirkita.pos.presentation.checkout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.data.model.buildV1TransactionRequest
import com.kasirkita.pos.domain.model.OfflineFinancialSnapshot
import com.kasirkita.pos.domain.repository.CartRepository
import com.kasirkita.pos.domain.repository.OutletRepository
import com.kasirkita.pos.domain.repository.ShiftRepository
import com.kasirkita.pos.domain.repository.TransactionRepository
import com.kasirkita.pos.domain.usecase.GetReceiptUseCase
import com.kasirkita.pos.domain.usecase.PrintAfterCheckoutUseCase
import com.kasirkita.pos.domain.usecase.QueueOfflineTransactionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val queueOfflineTransaction: QueueOfflineTransactionUseCase,
    private val cartRepository: CartRepository,
    outletRepository: OutletRepository,
    shiftRepository: ShiftRepository,
    private val getReceiptUseCase: GetReceiptUseCase,
    private val printAfterCheckoutUseCase: PrintAfterCheckoutUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(CheckoutState())
    val state: StateFlow<CheckoutState> = _state.asStateFlow()
    private val transactionIdentity = CheckoutTransactionIdentity(savedStateHandle)

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
                            payment = current.payment.withTotal(cart.totalAmount()),
                        )
                    }
                }
        }
    }

    fun selectPaymentMethod(method: CheckoutPaymentMethod) {
        _state.update { current ->
            current.copy(
                payment = when (method) {
                    CheckoutPaymentMethod.CASH -> current.payment.selectCash()
                    CheckoutPaymentMethod.QRIS -> current.payment.selectQris()
                },
                errorMessage = null,
            )
        }
    }

    fun selectExactCash() {
        _state.update { current ->
            current.copy(
                payment = current.payment.selectExactCash(),
                errorMessage = null,
            )
        }
    }

    fun selectQuickTender(amount: Long) {
        _state.update { current ->
            current.copy(
                payment = current.payment.selectTender(amount),
                errorMessage = null,
            )
        }
    }

    fun enterManualCash(input: String) {
        _state.update { current ->
            current.copy(
                payment = current.payment.enterManualCash(input),
                errorMessage = null,
            )
        }
    }

    fun confirmPayment() {
        val snapshot = _state.value
        if (
            snapshot.isLoading ||
            snapshot.transaction != null ||
            snapshot.offlineQueuedClientTransactionId != null
        ) {
            return
        }

        if (!snapshot.payment.canSubmit) {
            val error = when (snapshot.payment.method) {
                CheckoutPaymentMethod.CASH -> "Jumlah pembayaran kurang dari total belanja"
                CheckoutPaymentMethod.QRIS -> "Pembayaran QRIS tidak valid"
            }
            _state.update { it.copy(errorMessage = error) }
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
            else -> null
        }

        if (error != null) {
            _state.update { it.copy(errorMessage = error) }
            return
        }

        val outletId = outlet?.id ?: return
        val cashierSessionId = shift?.id ?: return
        val clientTransactionId = transactionIdentity.getOrCreate()

        // Mark submitting synchronously so a duplicate confirmPayment() before the
        // coroutine resumes is guarded by isLoading rather than launching twice.
        _state.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                persistedTotal = snapshot.cart.totalAmount(),
            )
        }

        viewModelScope.launch {
            val v1Request = buildV1TransactionRequest(
                clientTransactionId = clientTransactionId,
                outletId = outletId,
                cashierSessionId = cashierSessionId,
                items = snapshot.cart.items,
                payment = snapshot.payment.toPayment(),
                customerId = null,
                discount = null,
            )

            val onlineResult = transactionRepository.createV1Transaction(v1Request)

            val transaction = onlineResult.getOrNull()
            if (transaction != null) {
                cartRepository.clearCart()

                // Fetch canonical server receipt and trigger auto-print/drawer
                val receiptResult = getReceiptUseCase(transaction.id)
                val receipt = receiptResult.getOrNull()
                if (receipt != null) {
                    val paymentMethod = snapshot.payment.method.name // "CASH" or "QRIS"
                    val printResult = printAfterCheckoutUseCase(
                        receipt = receipt,
                        paymentMethod = paymentMethod,
                        isOriginalOnlineCheckout = true,
                    )
                    val printerWarning = when (printResult) {
                        is PrintAfterCheckoutUseCase.Result.Failure ->
                            printResult.printError ?: "Transaksi berhasil, tetapi struk gagal dicetak."
                        is PrintAfterCheckoutUseCase.Result.Success -> null
                    }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            transaction = transaction,
                            errorMessage = null,
                            printerWarning = printerWarning,
                        )
                    }
                    return@launch
                }

                _state.update {
                    it.copy(
                        isLoading = false,
                        transaction = transaction,
                        errorMessage = null,
                        printerWarning = null,
                    )
                }
                return@launch
            }

            val throwable = onlineResult.exceptionOrNull()
            if (throwable !is IOException) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = checkoutErrorMessage(throwable),
                    )
                }
                return@launch
            }

            queueOfflineTransaction(
                request = v1Request,
                financialSnapshot = OfflineFinancialSnapshot(
                    subtotal = snapshot.cart.totalAmount(),
                    discount = 0L,
                    tax = 0L,
                    total = snapshot.cart.totalAmount(),
                    paymentAmount = snapshot.payment.amountReceived ?: snapshot.cart.totalAmount(),
                ),
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

    /**
     * Backward-compatible CASH-only adapter for callers using the pre-M12 API.
     * QRIS always uses [confirmPayment], so this method cannot create a QRIS
     * request with a cash tender amount.
     */
    fun checkout(paymentAmount: Long) {
        val snapshot = _state.value
        if (
            snapshot.isLoading ||
            snapshot.transaction != null ||
            snapshot.offlineQueuedClientTransactionId != null
        ) {
            return
        }

        _state.update { current ->
            current.copy(
                payment = current.payment.selectCash().selectTender(paymentAmount),
                errorMessage = null,
            )
        }
        confirmPayment()
    }

    private companion object {
        const val OPEN_STATUS = "OPEN"
    }
}

internal class CheckoutTransactionIdentity(
    private val savedStateHandle: SavedStateHandle,
    private val createId: () -> String = { UUID.randomUUID().toString() },
) {
    fun getOrCreate(): String = savedStateHandle[KEY]
        ?: createId().also { generatedId -> savedStateHandle[KEY] = generatedId }

    internal companion object {
        const val KEY = "activeClientTransactionId"
    }
}

internal fun checkoutErrorMessage(throwable: Throwable?): String {
    if (throwable is HttpException) {
        val responseBody = runCatching {
            throwable.response()?.errorBody()?.string()
        }.getOrNull().orEmpty().uppercase(Locale.ROOT)

        if ("INSUFFICIENT_STOCK" in responseBody || "INSUFFICIENT STOCK" in responseBody) {
            return "Stok tidak mencukupi. Kurangi jumlah produk di cart lalu coba lagi."
        }
    }

    return throwable?.message ?: "Checkout gagal"
}
