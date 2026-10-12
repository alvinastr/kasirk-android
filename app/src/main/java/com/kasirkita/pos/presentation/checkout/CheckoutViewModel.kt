package com.kasirkita.pos.presentation.checkout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.kasirkita.pos.core.policy.HeldOrderPilotPolicy
import com.kasirkita.pos.data.model.buildV1TransactionRequest
import com.kasirkita.pos.domain.error.HeldOrderError
import com.kasirkita.pos.domain.model.HeldOrderCheckoutRequest
import com.kasirkita.pos.domain.model.OnlineTransactionJournalDraft
import com.kasirkita.pos.domain.model.Transaction
import com.kasirkita.pos.domain.model.V1Payment
import com.kasirkita.pos.domain.repository.CartRepository
import com.kasirkita.pos.domain.repository.HeldOrderRepository
import com.kasirkita.pos.domain.repository.OutletRepository
import com.kasirkita.pos.domain.repository.OnlineTransactionJournalRepository
import com.kasirkita.pos.domain.repository.ShiftRepository
import com.kasirkita.pos.domain.repository.TransactionRepository
import com.kasirkita.pos.domain.usecase.GetReceiptUseCase
import com.kasirkita.pos.domain.usecase.PrintAfterCheckoutUseCase
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
    private val cartRepository: CartRepository,
    private val heldOrderRepository: HeldOrderRepository,
    outletRepository: OutletRepository,
    shiftRepository: ShiftRepository,
    private val getReceiptUseCase: GetReceiptUseCase,
    private val printAfterCheckoutUseCase: PrintAfterCheckoutUseCase,
    savedStateHandle: SavedStateHandle,
    private val gson: Gson,
    private val onlineTransactionJournalRepository: OnlineTransactionJournalRepository,
    private val heldOrderPilotPolicy: HeldOrderPilotPolicy,
) : ViewModel() {

    private val transactionIdentity = CheckoutTransactionIdentity(savedStateHandle)
    private val pendingHeldOrder = PendingHeldOrderCheckoutContext(savedStateHandle)
    private val conflictContext = HeldOrderCheckoutConflictContext(savedStateHandle)
    private val recoveredAccountKeys = mutableSetOf<String>()

    private val _state = MutableStateFlow(
        CheckoutState(
            heldOrderConflict = conflictContext.conflict,
            reconciliationNeeded = pendingHeldOrder.reconciliationNeeded,
            reconciliationMessage = if (pendingHeldOrder.reconciliationNeeded) {
                RECONCILIATION_REQUIRED_MESSAGE
            } else null,
            errorMessage = if (pendingHeldOrder.reconciliationNeeded) {
                RECONCILIATION_REQUIRED_MESSAGE
            } else null,
        )
    )
    val state: StateFlow<CheckoutState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                    cartRepository.getCart(),
                    outletRepository.selectedOutlet,
                    shiftRepository.currentShift,
            ) { cart, outlet, shift -> Triple(cart, outlet, shift) }
                .collect { (cart, outlet, shift) ->
                    val accountKey = if (outlet != null && shift != null) {
                        "${outlet.tenantId}|${shift.userId}"
                    } else {
                        null
                    }
                    if (accountKey != null && recoveredAccountKeys.add(accountKey)) {
                        onlineTransactionJournalRepository.recoverSubmittingForAccount(
                            tenantId = outlet!!.tenantId,
                            userId = shift!!.userId,
                        )
                    }
                    val journalResult = if (outlet != null && shift != null) {
                        onlineTransactionJournalRepository.getUnresolvedForAccount(
                            tenantId = outlet.tenantId,
                            userId = shift.userId,
                        )
                    } else {
                        Result.success(null)
                    }
                    val unresolvedJournal = journalResult.getOrNull()
                    val journalReadFailed = journalResult.isFailure
                    val heldReconciliation = pendingHeldOrder.reconciliationNeeded
                    _state.update { current ->
                        current.copy(
                            cart = cart,
                            selectedOutlet = outlet,
                            currentShift = shift,
                            payment = current.payment.withTotal(cart.totalAmount()),
                            reconciliationNeeded = heldReconciliation ||
                                unresolvedJournal != null || journalReadFailed,
                            reconciliationMessage = when {
                                heldReconciliation -> RECONCILIATION_REQUIRED_MESSAGE
                                unresolvedJournal != null -> onlineJournalMessage(
                                    unresolvedJournal.clientTransactionId,
                                )
                                journalReadFailed -> JOURNAL_UNAVAILABLE_MESSAGE
                                else -> null
                            },
                            errorMessage = when {
                                heldReconciliation -> RECONCILIATION_REQUIRED_MESSAGE
                                unresolvedJournal != null -> onlineJournalMessage(
                                    unresolvedJournal.clientTransactionId,
                                )
                                journalReadFailed -> JOURNAL_UNAVAILABLE_MESSAGE
                                else -> current.errorMessage
                            },
                        )
                    }
                }
        }
    }

    fun selectPaymentMethod(method: CheckoutPaymentMethod) {
        if (_state.value.reconciliationNeeded && !cartRepository.isAttachedToHeldOrder()) return
        _state.update { current ->
            current.copy(
                payment = when (method) {
                    CheckoutPaymentMethod.CASH -> current.payment.selectCash()
                    CheckoutPaymentMethod.QRIS -> current.payment.selectQris()
                    CheckoutPaymentMethod.EDC -> current.payment.selectEdc()
                },
                errorMessage = null,
            )
        }
    }

    fun selectExactCash() {
        if (_state.value.reconciliationNeeded && !cartRepository.isAttachedToHeldOrder()) return
        _state.update { current ->
            current.copy(
                payment = current.payment.selectExactCash(),
                errorMessage = null,
            )
        }
    }

    fun selectQuickTender(amount: Long) {
        if (_state.value.reconciliationNeeded && !cartRepository.isAttachedToHeldOrder()) return
        _state.update { current ->
            current.copy(
                payment = current.payment.selectTender(amount),
                errorMessage = null,
            )
        }
    }

    fun enterManualCash(input: String) {
        if (_state.value.reconciliationNeeded && !cartRepository.isAttachedToHeldOrder()) return
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
            snapshot.offlineQueuedClientTransactionId != null ||
            snapshot.heldOrderConflict.requiresExplicitExit
        ) {
            return
        }

        if (cartRepository.isAttachedToHeldOrder() && !heldOrderPilotPolicy.allowsCheckout()) {
            _state.update { it.copy(errorMessage = HELD_ORDER_DISABLED_MESSAGE) }
            return
        }

        // M16G-S2: if an attached Held Order has an unresolved outcome,
        // any payment confirmation must route to the safe frozen retry.
        if (cartRepository.isAttachedToHeldOrder() && snapshot.reconciliationNeeded) {
            retryHeldOrderCheckout()
            return
        }

        if (snapshot.reconciliationNeeded) {
            retryOnlineCheckout()
            return
        }

        if (!snapshot.payment.canSubmit) {
            val error = when (snapshot.payment.method) {
                CheckoutPaymentMethod.CASH -> "Jumlah pembayaran kurang dari total belanja"
                CheckoutPaymentMethod.QRIS -> "Pembayaran QRIS tidak valid"
                CheckoutPaymentMethod.EDC -> "Pembayaran EDC tidak valid"
            }
            _state.update { it.copy(errorMessage = error) }
            return
        }

        // M16G-S1: a cart attached to an OPEN Held Order must settle
        // authoritatively through the Held Order endpoint. It must never go
        // through TransactionRepository nor the offline queue, because the
        // server owns the held order's items, prices and version.
        if (cartRepository.isAttachedToHeldOrder()) {
            confirmHeldOrderPayment(snapshot)
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

        val v1Request = buildV1TransactionRequest(
            clientTransactionId = clientTransactionId,
            outletId = outletId,
            cashierSessionId = cashierSessionId,
            items = snapshot.cart.items,
            payment = snapshot.payment.toPayment(),
            customerId = null,
            discount = null,
        )
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
            val journal = onlineTransactionJournalRepository.prepare(
                OnlineTransactionJournalDraft(
                    tenantId = outlet?.tenantId ?: return@launch,
                    userId = shift?.userId ?: return@launch,
                    outletId = outletId,
                    clientTransactionId = clientTransactionId,
                    requestType = REQUEST_TYPE_V1,
                    requestJson = gson.toJson(v1Request),
                    paymentMethod = snapshot.payment.method.name,
                ),
            ).getOrElse { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        reconciliationNeeded = true,
                        reconciliationMessage = JOURNAL_UNAVAILABLE_MESSAGE,
                        errorMessage = JOURNAL_UNAVAILABLE_MESSAGE,
                    )
                }
                return@launch
            }
            if (!onlineTransactionJournalRepository.markSubmitting(journal).getOrDefault(false)) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        reconciliationNeeded = true,
                        reconciliationMessage = onlineJournalMessage(clientTransactionId),
                        errorMessage = onlineJournalMessage(clientTransactionId),
                    )
                }
                return@launch
            }

            val onlineResult = transactionRepository.createV1Transaction(v1Request)

            val transaction = onlineResult.getOrNull()
            if (transaction != null) {
                val confirmed = onlineTransactionJournalRepository.markConfirmed(
                    journal,
                    transaction.id,
                ).getOrDefault(false)
                if (!confirmed) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            reconciliationNeeded = true,
                            reconciliationMessage = JOURNAL_UNAVAILABLE_MESSAGE,
                            errorMessage = JOURNAL_UNAVAILABLE_MESSAGE,
                        )
                    }
                    return@launch
                }
                completeAuthoritativeOnlineCheckout(
                    transaction = transaction,
                    paymentMethod = snapshot.payment.method.name,
                    runAutomaticPhysicalEffects = true,
                    receiptFailureWarning = null,
                )
                return@launch
            }

            val throwable = onlineResult.exceptionOrNull()
            if (throwable !is IOException) {
                val rejected = onlineTransactionJournalRepository.markRejected(
                    journal,
                    checkoutErrorMessage(throwable),
                ).getOrDefault(false)
                transactionIdentity.clear()
                _state.update {
                    it.copy(
                        isLoading = false,
                        reconciliationNeeded = !rejected,
                        reconciliationMessage = if (rejected) null else JOURNAL_UNAVAILABLE_MESSAGE,
                        errorMessage = if (rejected) checkoutErrorMessage(throwable) else JOURNAL_UNAVAILABLE_MESSAGE,
                    )
                }
                return@launch
            }

            onlineTransactionJournalRepository.markUncertain(
                journal,
                throwable.message ?: "Network outcome is unknown",
            )

            _state.update {
                it.copy(
                    isLoading = false,
                    reconciliationNeeded = true,
                    reconciliationMessage = onlineJournalMessage(clientTransactionId),
                    errorMessage = onlineJournalMessage(clientTransactionId),
                )
            }
        }
    }

    /**
     * M16G-S1 / S2: authoritative checkout for a cart attached to an OPEN Held Order.
     *
     * The Held Order endpoint owns the price truth, so nothing derived from the
     * local cart snapshot (prices, computed total) is transmitted and there is no
     * offline fallback: an offline queue entry could never convert a held order.
     *
     * In S2, pending request metadata is persisted in SavedStateHandle BEFORE network
     * submission so that transport failures (IOException) can be retried identically.
     */
    private fun confirmHeldOrderPayment(snapshot: CheckoutState) {
        // CartRepository stays the single owner of the attached identity; this
        // ViewModel only reads it for the request it has to build.
        val attachedIdentity = cartRepository.getHeldOrderIdentity().value
        val outlet = snapshot.selectedOutlet
        val shift = snapshot.currentShift

        val contextError = when {
            attachedIdentity == null -> "Order tersimpan tidak ditemukan"
            outlet == null -> "Outlet belum dipilih"
            shift == null || !shift.status.equals(OPEN_STATUS, ignoreCase = true) ->
                "Tidak ada shift aktif"
            shift.outletId != outlet.id -> "Shift aktif tidak sesuai dengan outlet yang dipilih"
            else -> null
        }

        if (contextError != null) {
            _state.update { it.copy(errorMessage = contextError) }
            return
        }

        val identity = checkNotNull(attachedIdentity)

        // Same stable identity as the normal path: minted once, then never
        // regenerated, so a retry after an unresolved failure keeps the same
        // client_transaction_id and therefore stays idempotent.
        val clientTransactionId = transactionIdentity.getOrCreate()

        val paymentMethod = snapshot.payment.method.name
        val amountReceived = if (snapshot.payment.method == CheckoutPaymentMethod.CASH) {
            snapshot.payment.amountReceived
        } else {
            null
        }

        // M16G-S2: persist pending metadata BEFORE network submission
        pendingHeldOrder.recordPending(
            heldOrderId = identity.heldOrderId,
            expectedVersion = identity.expectedVersion,
            clientTransactionId = clientTransactionId,
            paymentMethod = paymentMethod,
            amountReceived = amountReceived,
            // Treat the request as unresolved before crossing the network boundary.
            // If the process dies after submission, recreation must reconcile the
            // frozen request rather than silently constructing a new payload.
            reconciliationNeeded = true,
        )

        // Mark submitting synchronously so a duplicate confirmPayment() before the
        // coroutine resumes is guarded by isLoading rather than launching twice.
        _state.update {
            it.copy(
                isLoading = true,
                heldOrderCheckoutSubmitting = true,
                errorMessage = null,
                reconciliationMessage = null,
            )
        }

        viewModelScope.launch {
            val result = heldOrderRepository.checkout(
                HeldOrderCheckoutRequest(
                    id = identity.heldOrderId,
                    expectedVersion = identity.expectedVersion,
                    clientTransactionId = clientTransactionId,
                    payment = snapshot.payment.toPayment(),
                ),
            )

            val checkoutResult = result.getOrNull()
            if (checkoutResult != null) {
                completeHeldOrderCheckoutSuccess(
                    transaction = checkoutResult.transaction,
                    replayed = checkoutResult.replayed,
                    paymentMethod = paymentMethod,
                )
                return@launch
            }

            val throwable = result.exceptionOrNull()
            if (throwable is IOException) {
                // Initial IOException: transport failure -> outcome unknown -> reconciliation needed
                pendingHeldOrder.reconciliationNeeded = true
                _state.update {
                    it.copy(
                        isLoading = false,
                        heldOrderCheckoutSubmitting = false,
                        reconciliationNeeded = true,
                        reconciliationMessage = RECONCILIATION_REQUIRED_MESSAGE,
                        errorMessage = heldOrderCheckoutErrorMessage(throwable),
                    )
                }
                return@launch
            }

            // Deterministic failure on initial request:
            // Server definitively rejected request -> no unknown outcome -> clear pending metadata
            val conflict = classifyHeldOrderConflict(throwable)
            pendingHeldOrder.clear()
            conflictContext.conflict = conflict
            _state.update {
                it.copy(
                    isLoading = false,
                    heldOrderCheckoutSubmitting = false,
                    reconciliationNeeded = false,
                    reconciliationMessage = null,
                    heldOrderConflict = conflict,
                    errorMessage = heldOrderCheckoutErrorMessage(throwable),
                )
            }
        }
    }

    /**
     * M16G-S2: explicit retry/reconciliation action for an unresolved Held Order checkout.
     *
     * When [state.reconciliationNeeded] is true, reconstructs the checkout request
     * strictly from the frozen pending metadata in SavedStateHandle. The request
     * is completely immune to any UI payment changes.
     */
    fun leaveHeldOrderConflict() {
        val snapshot = _state.value
        if (snapshot.reconciliationNeeded || snapshot.heldOrderCheckoutSubmitting) {
            if (snapshot.reconciliationNeeded && !cartRepository.isAttachedToHeldOrder()) {
                _state.update {
                    it.copy(errorMessage = RECONCILIATION_REQUIRED_MESSAGE)
                }
                return
            }
            _state.update { it.copy(showUnresolvedReconciliationLeaveConfirmation = true) }
            return
        }
        clearHeldOrderCheckoutContext()
    }

    fun dismissUnresolvedReconciliationLeaveConfirmation() {
        _state.update { it.copy(showUnresolvedReconciliationLeaveConfirmation = false) }
    }

    fun acknowledgeAndLeaveUnresolvedReconciliation() {
        if (cartRepository.isAttachedToHeldOrder()) {
            pendingHeldOrder.clear()
        }
        clearHeldOrderCheckoutContext()
    }

    private fun clearHeldOrderCheckoutContext() {
        transactionIdentity.clear()
        conflictContext.clear()
        cartRepository.detachHeldOrderIdentity()
        cartRepository.clearCart()
        _state.update {
            it.copy(
                isLoading = false,
                heldOrderCheckoutSubmitting = false,
                errorMessage = null,
                heldOrderConflict = HeldOrderCheckoutConflict.NONE,
                reconciliationNeeded = false,
                reconciliationMessage = null,
                showUnresolvedReconciliationLeaveConfirmation = false,
            )
        }
    }

    fun retryHeldOrderCheckout() {
        if (!heldOrderPilotPolicy.allowsCheckout()) {
            _state.update { it.copy(errorMessage = HELD_ORDER_DISABLED_MESSAGE) }
            return
        }
        val snapshot = _state.value
        if (snapshot.isLoading || !pendingHeldOrder.reconciliationNeeded) {
            return
        }

        // Guard duplicate retry taps synchronously before coroutine suspension
        _state.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
            )
        }

        val pending = pendingHeldOrder.getPending()
        if (pending == null) {
            _state.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Data konfirmasi ulang tidak ditemukan",
                )
            }
            return
        }

        // Validate current operational context
        val attachedIdentity = cartRepository.getHeldOrderIdentity().value
        val outlet = snapshot.selectedOutlet
        val shift = snapshot.currentShift

        val contextError = when {
            attachedIdentity == null -> "Order tersimpan tidak ditemukan"
            attachedIdentity.heldOrderId != pending.heldOrderId ->
                "Order tersimpan tidak sesuai dengan transaksi yang memerlukan konfirmasi ulang"
            outlet == null -> "Outlet belum dipilih"
            shift == null || !shift.status.equals(OPEN_STATUS, ignoreCase = true) ->
                "Tidak ada shift aktif"
            shift.outletId != outlet.id -> "Shift aktif tidak sesuai dengan outlet yang dipilih"
            else -> null
        }

        if (contextError != null) {
            _state.update {
                it.copy(
                    isLoading = false,
                    errorMessage = contextError,
                )
            }
            return
        }

        viewModelScope.launch {
            // Reconstruct the frozen payment payload from persisted metadata only.
            // QRIS and EDC can never carry a cash tender amount; CASH keeps its original one.
            val payment = when (pending.paymentMethod) {
                V1_PAYMENT_METHOD_QRIS -> V1Payment(method = V1_PAYMENT_METHOD_QRIS, amountReceived = null)
                V1_PAYMENT_METHOD_EDC -> V1Payment(method = V1_PAYMENT_METHOD_EDC, amountReceived = null)
                else -> V1Payment(method = pending.paymentMethod, amountReceived = pending.amountReceived)
            }

            val request = HeldOrderCheckoutRequest(
                id = pending.heldOrderId,
                expectedVersion = pending.expectedVersion,
                clientTransactionId = pending.clientTransactionId,
                payment = payment,
            )

            val result = heldOrderRepository.checkout(request)

            val checkoutResult = result.getOrNull()
            if (checkoutResult != null) {
                completeHeldOrderCheckoutSuccess(
                    transaction = checkoutResult.transaction,
                    replayed = checkoutResult.replayed,
                    paymentMethod = pending.paymentMethod,
                )
                return@launch
            }

            val throwable = result.exceptionOrNull()
            if (throwable is IOException) {
                // Repeated IOException: pending metadata remains unchanged, reconciliationNeeded remains true
                _state.update {
                    it.copy(
                        isLoading = false,
                        heldOrderCheckoutSubmitting = false,
                        reconciliationNeeded = true,
                        reconciliationMessage = RECONCILIATION_REQUIRED_MESSAGE,
                        errorMessage = heldOrderCheckoutErrorMessage(throwable),
                    )
                }
                return@launch
            }

            // Server returned error during reconciliation (e.g. IDEMPOTENCY_PAYLOAD_MISMATCH, HELD_ORDER_NOT_OPEN, etc.)
            // Retain reconciliation state safely, cart/identity preserved, pending metadata retained, no hardware
            val conflict = classifyHeldOrderConflict(throwable)
            conflictContext.conflict = conflict
            _state.update {
                it.copy(
                    isLoading = false,
                    heldOrderCheckoutSubmitting = false,
                    reconciliationNeeded = true,
                    reconciliationMessage = RECONCILIATION_REQUIRED_MESSAGE,
                    heldOrderConflict = conflict,
                    errorMessage = heldOrderCheckoutErrorMessage(throwable),
                )
            }
        }
    }

    /**
     * Replays an ordinary online checkout after an unknown transport outcome.
     * The exact request persisted before the first network call is reused; no
     * queue row is created and no new client transaction ID is minted.
     */
    fun retryOnlineCheckout() {
        val snapshot = _state.value
        if (snapshot.isLoading || !snapshot.reconciliationNeeded) return

        viewModelScope.launch {
            val outlet = snapshot.selectedOutlet
            val shift = snapshot.currentShift
            val journal = if (outlet != null && shift != null) {
                onlineTransactionJournalRepository.getUnresolvedForAccount(
                    tenantId = outlet.tenantId,
                    userId = shift.userId,
                ).getOrNull()
            } else {
                null
            }
            if (journal == null || journal.requestType != REQUEST_TYPE_V1) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        reconciliationNeeded = true,
                        errorMessage = JOURNAL_UNAVAILABLE_MESSAGE,
                    )
                }
                return@launch
            }
            val request = runCatching {
                gson.fromJson(
                    journal.requestJson,
                    com.kasirkita.pos.domain.model.V1TransactionRequest::class.java,
                )
            }.getOrNull()
            if (request == null || request.clientTransactionId != journal.clientTransactionId) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        reconciliationNeeded = true,
                        errorMessage = JOURNAL_UNAVAILABLE_MESSAGE,
                    )
                }
                return@launch
            }
            if (!onlineTransactionJournalRepository.markSubmitting(journal).getOrDefault(false)) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        reconciliationNeeded = true,
                        errorMessage = onlineJournalMessage(journal.clientTransactionId),
                    )
                }
                return@launch
            }
            _state.update { it.copy(isLoading = true, errorMessage = null) }

            val result = transactionRepository.createV1Transaction(request)
            val transaction = result.getOrNull()
            if (transaction != null) {
                if (!onlineTransactionJournalRepository.markConfirmed(journal, transaction.id)
                        .getOrDefault(false)
                ) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            reconciliationNeeded = true,
                            errorMessage = JOURNAL_UNAVAILABLE_MESSAGE,
                        )
                    }
                    return@launch
                }
                completeAuthoritativeOnlineCheckout(
                    transaction = transaction,
                    paymentMethod = request.payment.method,
                    runAutomaticPhysicalEffects = true,
                    receiptFailureWarning = null,
                )
                return@launch
            }

            val throwable = result.exceptionOrNull()
            if (throwable is IOException) {
                onlineTransactionJournalRepository.markUncertain(
                    journal,
                    throwable.message ?: "Network outcome is unknown",
                )
            } else {
                onlineTransactionJournalRepository.markRejected(
                    journal,
                    checkoutErrorMessage(throwable),
                )
                transactionIdentity.clear()
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    reconciliationNeeded = throwable is IOException,
                    reconciliationMessage = if (throwable is IOException) {
                        onlineJournalMessage(journal.clientTransactionId)
                    } else null,
                    errorMessage = if (throwable is IOException) {
                        onlineJournalMessage(journal.clientTransactionId)
                    } else checkoutErrorMessage(throwable),
                )
            }
        }
    }

    fun retryPayment() {
        if (cartRepository.isAttachedToHeldOrder()) {
            retryHeldOrderCheckout()
        } else {
            retryOnlineCheckout()
        }
    }

    private suspend fun completeHeldOrderCheckoutSuccess(
        transaction: Transaction,
        replayed: Boolean,
        paymentMethod: String,
    ) {
        pendingHeldOrder.clear()
        conflictContext.clear()
        cartRepository.detachHeldOrderIdentity()
        completeAuthoritativeOnlineCheckout(
            transaction = transaction,
            paymentMethod = transaction.payments.firstOrNull()?.method ?: paymentMethod,
            runAutomaticPhysicalEffects = !replayed,
            receiptFailureWarning = "Transaksi berhasil, tetapi struk gagal dimuat.",
            heldOrderReplayed = replayed,
        )
    }

    private suspend fun completeAuthoritativeOnlineCheckout(
        transaction: Transaction,
        paymentMethod: String,
        runAutomaticPhysicalEffects: Boolean,
        receiptFailureWarning: String?,
        heldOrderReplayed: Boolean? = null,
    ) {
        if (heldOrderReplayed == null) {
            transactionIdentity.clear()
        }
        cartRepository.clearCart()

        if (!runAutomaticPhysicalEffects) {
            _state.update {
                it.copy(
                    isLoading = false,
                    heldOrderCheckoutSubmitting = false,
                    transaction = transaction,
                    heldOrderReplayed = heldOrderReplayed,
                    reconciliationNeeded = false,
                    reconciliationMessage = null,
                    heldOrderConflict = HeldOrderCheckoutConflict.NONE,
                    errorMessage = null,
                    printerWarning = null,
                )
            }
            return
        }

        val receiptResult = getReceiptUseCase(transaction.id)
        val receipt = receiptResult.getOrNull()
        if (receipt == null) {
            _state.update {
                it.copy(
                    isLoading = false,
                    heldOrderCheckoutSubmitting = false,
                    transaction = transaction,
                    heldOrderReplayed = heldOrderReplayed,
                    reconciliationNeeded = false,
                    reconciliationMessage = null,
                    heldOrderConflict = HeldOrderCheckoutConflict.NONE,
                    errorMessage = null,
                    printerWarning = receiptFailureWarning,
                )
            }
            return
        }

        val printResult = printAfterCheckoutUseCase(
            receipt = receipt,
            paymentMethod = paymentMethod,
            isOriginalOnlineCheckout = true,
        )
        val printerWarning = when (printResult) {
            is PrintAfterCheckoutUseCase.Result.Failure ->
                printResult.printError ?: printResult.drawerError ?: "Transaksi berhasil, tetapi struk gagal dicetak."
            is PrintAfterCheckoutUseCase.Result.Success -> null
        }
        _state.update {
            it.copy(
                isLoading = false,
                heldOrderCheckoutSubmitting = false,
                transaction = transaction,
                heldOrderReplayed = heldOrderReplayed,
                reconciliationNeeded = false,
                reconciliationMessage = null,
                heldOrderConflict = HeldOrderCheckoutConflict.NONE,
                errorMessage = null,
                printerWarning = printerWarning,
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
            snapshot.offlineQueuedClientTransactionId != null ||
            snapshot.reconciliationNeeded ||
            snapshot.heldOrderConflict.requiresExplicitExit
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
        const val RECONCILIATION_REQUIRED_MESSAGE =
            "Status pembayaran belum dapat dipastikan karena kendala koneksi. Silakan lakukan konfirmasi ulang."
        const val JOURNAL_UNAVAILABLE_MESSAGE =
            "Jurnal transaksi belum tersimpan atau tidak dapat dibaca. Transaksi diblokir; periksa perangkat sebelum melanjutkan."
        const val HELD_ORDER_DISABLED_MESSAGE =
            "Checkout order tersimpan dinonaktifkan untuk pilot ini."
        const val REQUEST_TYPE_V1 = "V1_TRANSACTION"

        fun onlineJournalMessage(clientTransactionId: String): String =
            "Status transaksi belum dapat dipastikan. Jangan kirim ulang dengan ID baru. Periksa riwayat server menggunakan ID klien $clientTransactionId sebelum melanjutkan."
    }
}

internal class CheckoutTransactionIdentity(
    private val savedStateHandle: SavedStateHandle,
    private val createId: () -> String = { UUID.randomUUID().toString() },
) {
    fun getOrCreate(): String = savedStateHandle[KEY]
        ?: createId().also { generatedId -> savedStateHandle[KEY] = generatedId }

    fun clear() {
        savedStateHandle.remove<String>(KEY)
    }

    internal companion object {
        const val KEY = "activeClientTransactionId"
    }
}

internal data class PendingHeldOrderCheckout(
    val heldOrderId: String,
    val expectedVersion: Int,
    val clientTransactionId: String,
    val paymentMethod: String,
    val amountReceived: Long?,
)

internal class PendingHeldOrderCheckoutContext(
    private val savedStateHandle: SavedStateHandle,
) {
    var heldOrderId: String?
        get() = savedStateHandle.get<String>(KEY_HELD_ORDER_ID)?.takeIf { it.isNotBlank() }
        private set(value) {
            if (value.isNullOrBlank()) {
                savedStateHandle.remove<String>(KEY_HELD_ORDER_ID)
            } else {
                savedStateHandle[KEY_HELD_ORDER_ID] = value
            }
        }

    var expectedVersion: Int?
        get() = savedStateHandle.get<Int>(KEY_EXPECTED_VERSION)
        private set(value) {
            if (value == null) {
                savedStateHandle.remove<Int>(KEY_EXPECTED_VERSION)
            } else {
                savedStateHandle[KEY_EXPECTED_VERSION] = value
            }
        }

    var clientTransactionId: String?
        get() = savedStateHandle.get<String>(KEY_CLIENT_TX_ID)?.takeIf { it.isNotBlank() }
        private set(value) {
            if (value.isNullOrBlank()) {
                savedStateHandle.remove<String>(KEY_CLIENT_TX_ID)
            } else {
                savedStateHandle[KEY_CLIENT_TX_ID] = value
            }
        }

    var paymentMethod: String?
        get() = savedStateHandle.get<String>(KEY_PAYMENT_METHOD)?.takeIf { it.isNotBlank() }
        private set(value) {
            if (value.isNullOrBlank()) {
                savedStateHandle.remove<String>(KEY_PAYMENT_METHOD)
            } else {
                savedStateHandle[KEY_PAYMENT_METHOD] = value
            }
        }

    var amountReceived: Long?
        get() = savedStateHandle.get<Long>(KEY_AMOUNT_RECEIVED)
        private set(value) {
            if (value == null) {
                savedStateHandle.remove<Long>(KEY_AMOUNT_RECEIVED)
            } else {
                savedStateHandle[KEY_AMOUNT_RECEIVED] = value
            }
        }

    var reconciliationNeeded: Boolean
        get() = savedStateHandle.get<Boolean>(KEY_RECONCILIATION_NEEDED) ?: false
        set(value) {
            savedStateHandle[KEY_RECONCILIATION_NEEDED] = value
        }

    fun recordPending(
        heldOrderId: String,
        expectedVersion: Int,
        clientTransactionId: String,
        paymentMethod: String,
        amountReceived: Long?,
        reconciliationNeeded: Boolean = false,
    ) {
        this.heldOrderId = heldOrderId
        this.expectedVersion = expectedVersion
        this.clientTransactionId = clientTransactionId
        this.paymentMethod = paymentMethod
        this.amountReceived = amountReceived
        this.reconciliationNeeded = reconciliationNeeded
    }

    fun getPending(): PendingHeldOrderCheckout? {
        val id = heldOrderId ?: return null
        val ver = expectedVersion ?: return null
        val txId = clientTransactionId ?: return null
        val method = paymentMethod ?: return null
        return PendingHeldOrderCheckout(
            heldOrderId = id,
            expectedVersion = ver,
            clientTransactionId = txId,
            paymentMethod = method,
            amountReceived = amountReceived,
        )
    }

    /** Raw key view of the persisted state; used to assert write ordering. */
    fun snapshot(): Map<String, Any?> = mapOf(
        KEY_HELD_ORDER_ID to savedStateHandle.get<String>(KEY_HELD_ORDER_ID),
        KEY_EXPECTED_VERSION to savedStateHandle.get<Int>(KEY_EXPECTED_VERSION),
        KEY_CLIENT_TX_ID to savedStateHandle.get<String>(KEY_CLIENT_TX_ID),
        KEY_PAYMENT_METHOD to savedStateHandle.get<String>(KEY_PAYMENT_METHOD),
        KEY_AMOUNT_RECEIVED to savedStateHandle.get<Long>(KEY_AMOUNT_RECEIVED),
        KEY_RECONCILIATION_NEEDED to savedStateHandle.get<Boolean>(KEY_RECONCILIATION_NEEDED),
    )

    fun clear() {
        heldOrderId = null
        expectedVersion = null
        clientTransactionId = null
        paymentMethod = null
        amountReceived = null
        reconciliationNeeded = false
    }

    internal companion object {
        const val KEY_HELD_ORDER_ID = "pendingHeldOrderId"
        const val KEY_EXPECTED_VERSION = "pendingExpectedVersion"
        const val KEY_CLIENT_TX_ID = "pendingClientTxId"
        const val KEY_PAYMENT_METHOD = "pendingPaymentMethod"
        const val KEY_AMOUNT_RECEIVED = "pendingAmountReceived"
        const val KEY_RECONCILIATION_NEEDED = "pendingReconciliationNeeded"
    }
}

internal class HeldOrderCheckoutConflictContext(
    private val savedStateHandle: SavedStateHandle,
) {
    var conflict: HeldOrderCheckoutConflict
        get() = savedStateHandle.get<String>(KEY)
            ?.let { value -> runCatching { HeldOrderCheckoutConflict.valueOf(value) }.getOrNull() }
            ?: HeldOrderCheckoutConflict.NONE
        set(value) {
            if (value == HeldOrderCheckoutConflict.NONE) {
                savedStateHandle.remove<String>(KEY)
            } else {
                savedStateHandle[KEY] = value.name
            }
        }

    fun clear() {
        conflict = HeldOrderCheckoutConflict.NONE
    }

    internal companion object {
        const val KEY = "heldOrderCheckoutConflict"
    }
}

internal fun classifyHeldOrderConflict(throwable: Throwable?): HeldOrderCheckoutConflict =
    when ((throwable as? HeldOrderError)?.errorCode) {
        "HELD_ORDER_VERSION_CONFLICT" -> HeldOrderCheckoutConflict.VERSION_CONFLICT
        "HELD_ORDER_NOT_OPEN" -> HeldOrderCheckoutConflict.NOT_OPEN
        "IDEMPOTENCY_PAYLOAD_MISMATCH" -> HeldOrderCheckoutConflict.IDEMPOTENCY_MISMATCH
        "INVALID_CASHIER_SESSION" -> HeldOrderCheckoutConflict.SESSION_INVALID
        "INSUFFICIENT_STOCK" -> HeldOrderCheckoutConflict.STOCK_CHANGED
        null -> HeldOrderCheckoutConflict.GENERIC
        else -> HeldOrderCheckoutConflict.GENERIC
    }

internal fun heldOrderCheckoutErrorMessage(throwable: Throwable?): String {
    return when ((throwable as? HeldOrderError)?.errorCode) {
        "HELD_ORDER_VERSION_CONFLICT" ->
            "Order tersimpan telah berubah. Kembali ke daftar order tersimpan untuk memuat versi terbaru."
        "HELD_ORDER_NOT_OPEN" ->
            "Order tersimpan ini tidak lagi terbuka. Periksa kembali daftar order tersimpan."
        "IDEMPOTENCY_PAYLOAD_MISMATCH" ->
            "Percobaan pembayaran sebelumnya tidak dapat dikirim ulang dengan data pembayaran yang berbeda. Status memerlukan perhatian."
        "INVALID_CASHIER_SESSION" ->
            "Shift aktif yang valid diperlukan untuk memproses order tersimpan."
        "INSUFFICIENT_STOCK" ->
            "Stok tidak mencukupi. Periksa jumlah produk sebelum mencoba lagi."
        "CASH_UNDERPAYMENT" ->
            "Jumlah pembayaran tunai kurang dari total transaksi."
        else -> when (throwable) {
            is HeldOrderError -> "Order tersimpan tidak dapat diproses. Periksa kembali sebelum mencoba lagi."
            is IOException -> throwable.message ?: "Koneksi terputus saat memproses order tersimpan"
            else -> throwable?.message ?: "Checkout order tersimpan gagal"
        }
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
