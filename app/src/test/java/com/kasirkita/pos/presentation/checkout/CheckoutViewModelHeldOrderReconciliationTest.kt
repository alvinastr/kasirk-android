package com.kasirkita.pos.presentation.checkout

import androidx.lifecycle.SavedStateHandle
import com.kasirkita.pos.data.repository.CartRepositoryImpl
import com.kasirkita.pos.domain.error.HeldOrderError
import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.CartLineKey
import com.kasirkita.pos.domain.model.HeldOrder
import com.kasirkita.pos.domain.model.HeldOrderCheckoutRequest
import com.kasirkita.pos.domain.model.HeldOrderCheckoutResult
import com.kasirkita.pos.domain.model.HeldOrderCreateRequest
import com.kasirkita.pos.domain.model.HeldOrderUpdateRequest
import com.kasirkita.pos.domain.model.OfflineFinancialSnapshot
import com.kasirkita.pos.domain.model.OfflineQueueSummary
import com.kasirkita.pos.domain.model.OfflineTransaction
import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.domain.model.Payment
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.ReceiptCashier
import com.kasirkita.pos.domain.model.ReceiptOutlet
import com.kasirkita.pos.domain.model.ReceiptTenant
import com.kasirkita.pos.domain.model.Shift
import com.kasirkita.pos.domain.model.ShiftSummary
import com.kasirkita.pos.domain.model.SyncOutcome
import com.kasirkita.pos.domain.model.Transaction
import com.kasirkita.pos.domain.model.V1TransactionRequest
import com.kasirkita.pos.domain.repository.HeldOrderRepository
import com.kasirkita.pos.domain.repository.OfflineSyncRepository
import com.kasirkita.pos.domain.repository.OutletRepository
import com.kasirkita.pos.domain.repository.ReceiptRepository
import com.kasirkita.pos.domain.repository.ShiftRepository
import com.kasirkita.pos.domain.repository.TransactionRepository
import com.kasirkita.pos.domain.usecase.GetReceiptUseCase
import com.kasirkita.pos.domain.usecase.PrintAfterCheckoutUseCase
import com.kasirkita.pos.domain.usecase.QueueOfflineTransactionUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * M16G-S2: Held Order unknown-outcome recovery and stable retry.
 *
 * Covers the 20 required proofs from the slice brief:
 * pending-metadata persistence, IOException handling, frozen request identity and
 * payment across retries, process recreation, deterministic-error handling,
 * replay-aware success lifecycle and zero physical side effects.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelHeldOrderReconciliationTest {

    private val dispatcher = StandardTestDispatcher()
    private val cart = CartRepositoryImpl()
    private val transactions = RecordingTransactionRepository()
    private val offlineQueue = RecordingOfflineQueue()
    private val heldOrders = RecordingHeldOrderRepository()
    private val receipts = RecordingReceiptRepository()
    private val printUseCase = RecordingPrintAfterCheckoutUseCase()

    private val outlet = Outlet("outlet-1", "tenant-1", "Main", null, true, "now")
    private val shift = Shift("session-1", outlet.id, "cashier-1", null, null, null, null, "OPEN", "now", null)

    private val heldOrderId = "held-order-1"
    private val expectedVersion = 7
    private val clientTxId = "client-tx-1"
    private val cartTotal = 50_000L

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun seedCart(price: Long = cartTotal, quantity: Int = 1) {
        val product = Product(
            id = "prod-1",
            tenantId = "tenant-1",
            categoryId = "cat-1",
            name = "Latte",
            sku = "LT",
            price = price,
            cost = 10_000,
            minimumStock = 0,
            trackStock = false,
            isActive = true,
            createdAt = "now",
        )
        cart.addProduct(product)
        if (quantity > 1) {
            val key = cart.getCart().value.items.first().lineKey.value
            cart.updateQuantity(key, quantity)
        }
        cart.attachHeldOrderIdentity(heldOrderId = heldOrderId, expectedVersion = expectedVersion, label = null)
    }

    private fun freshHandle(): SavedStateHandle = SavedStateHandle(
        mapOf(CheckoutTransactionIdentity.KEY to clientTxId),
    )

    private fun viewModel(handle: SavedStateHandle = freshHandle()): CheckoutViewModel =
        CheckoutViewModel(
            transactionRepository = transactions,
            queueOfflineTransaction = QueueOfflineTransactionUseCase(offlineQueue),
            cartRepository = cart,
            heldOrderRepository = heldOrders,
            outletRepository = FakeOutlet(outlet),
            shiftRepository = FakeShift(shift),
            getReceiptUseCase = GetReceiptUseCase(receipts),
            printAfterCheckoutUseCase = printUseCase,
            savedStateHandle = handle,
        )

    private fun canonicalTransaction(id: String = "server-tx-1"): Transaction = Transaction(
        id = id,
        clientTransactionId = clientTxId,
        outletId = outlet.id,
        userId = "cashier-1",
        customerId = null,
        cashierSessionId = shift.id,
        shiftId = shift.id,
        status = "COMPLETED",
        subtotal = cartTotal,
        discount = 0,
        tax = 0,
        total = cartTotal,
        items = emptyList(),
        payments = listOf(Payment("pay-1", "CASH", "COMPLETED", cartTotal, "now", cartTotal, 0)),
        change = 0,
        createdAt = "now",
    )

    // ---------------------------------------------------------------------
    // 1. first Held checkout persists pending metadata before repository call
    // ---------------------------------------------------------------------
    @Test
    fun firstCheckout_persistsPendingMetadataBeforeRepositoryCall() = runTest(dispatcher) {
        seedCart()
        val handle = freshHandle()
        val observed = mutableListOf<Map<String, Any?>>()
        heldOrders.onCall = { observed += PendingHeldOrderCheckoutContext(handle).snapshot() }

        val vm = viewModel(handle)
        runCurrent()
        vm.selectQuickTender(cartTotal)
        vm.confirmPayment()
        advanceUntilIdle()

        // Metadata was already complete when the repository was entered.
        assertEquals(1, observed.size)
        with(observed.single()) {
            assertEquals(heldOrderId, this[PendingHeldOrderCheckoutContext.KEY_HELD_ORDER_ID])
            assertEquals(expectedVersion, this[PendingHeldOrderCheckoutContext.KEY_EXPECTED_VERSION])
            assertEquals(clientTxId, this[PendingHeldOrderCheckoutContext.KEY_CLIENT_TX_ID])
            assertEquals("CASH", this[PendingHeldOrderCheckoutContext.KEY_PAYMENT_METHOD])
            assertEquals(cartTotal, this[PendingHeldOrderCheckoutContext.KEY_AMOUNT_RECEIVED])
        }
    }

    // ---------------------------------------------------------------------
    // 2. first IOException: reconciliationNeeded, cart/identity/identity kept
    // ---------------------------------------------------------------------
    @Test
    fun firstIOException_preservesCartIdentityClientTxIdAndPayment() = runTest(dispatcher) {
        seedCart()
        val handle = freshHandle()
        val vm = viewModel(handle)
        runCurrent()
        heldOrders.checkoutResult = Result.failure(IOException("lost response"))

        vm.selectQuickTender(cartTotal)
        vm.confirmPayment()
        advanceUntilIdle()

        assertTrue(vm.state.value.reconciliationNeeded)
        assertNotNull(vm.state.value.reconciliationMessage)
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.transaction)

        // Cart and attached identity preserved.
        assertEquals(1, cart.getCart().value.items.size)
        assertEquals(heldOrderId, cart.getHeldOrderIdentity().value?.heldOrderId)

        // Exact pending request preserved in SavedStateHandle.
        val pending = PendingHeldOrderCheckoutContext(handle).getPending()
        assertNotNull(pending)
        assertEquals(heldOrderId, pending!!.heldOrderId)
        assertEquals(expectedVersion, pending.expectedVersion)
        assertEquals(clientTxId, pending.clientTransactionId)
        assertEquals("CASH", pending.paymentMethod)
        assertEquals(cartTotal, pending.amountReceived)

        // No offline queue, no receipt fetch, no hardware.
        assertEquals(0, offlineQueue.queueCalls.size)
        assertEquals(0, receipts.requestedIds.size)
        assertEquals(0, printUseCase.invocations.size)
    }

    // ---------------------------------------------------------------------
    // 3/4/5. retry reuses exact heldOrderId, expectedVersion, clientTransactionId
    // ---------------------------------------------------------------------
    @Test
    fun retry_reusesSameClientTxIdExpectedVersionAndHeldOrderId() = runTest(dispatcher) {
        seedCart()
        val handle = freshHandle()
        val vm = viewModel(handle)
        runCurrent()

        heldOrders.checkoutResult = Result.failure(IOException("lost response"))
        vm.selectQuickTender(cartTotal)
        vm.confirmPayment()
        advanceUntilIdle()

        heldOrders.checkoutResult = Result.success(
            HeldOrderCheckoutResult(canonicalTransaction(), replayed = true),
        )
        vm.retryHeldOrderCheckout()
        advanceUntilIdle()

        assertEquals(2, heldOrders.checkoutCalls.size)
        val first = heldOrders.checkoutCalls[0]
        val retry = heldOrders.checkoutCalls[1]
        assertEquals(first, retry)
        assertEquals(heldOrderId, retry.id)
        assertEquals(expectedVersion, retry.expectedVersion)
        assertEquals(clientTxId, retry.clientTransactionId)
        assertEquals(cartTotal, retry.payment.amountReceived)
    }

    // ---------------------------------------------------------------------
    // 6. CASH amountReceived frozen across retry even if UI payment changes
    // ---------------------------------------------------------------------
    @Test
    fun cashAmountReceived_isFrozenAcrossRetryEvenWhenUiPaymentChanges() = runTest(dispatcher) {
        seedCart()
        val handle = freshHandle()
        val vm = viewModel(handle)
        runCurrent()

        heldOrders.checkoutResult = Result.failure(IOException("lost response"))
        vm.selectQuickTender(50_000)
        vm.confirmPayment()
        advanceUntilIdle()

        // Cashier edits the UI payment to a completely different tender.
        vm.selectQuickTender(100_000)
        vm.enterManualCash("100000")
        assertEquals(100_000L, vm.state.value.payment.amountReceived)

        heldOrders.checkoutResult = Result.success(
            HeldOrderCheckoutResult(canonicalTransaction(), replayed = false),
        )
        vm.retryHeldOrderCheckout()
        advanceUntilIdle()

        val retry = heldOrders.checkoutCalls.last()
        assertEquals("CASH", retry.payment.method)
        assertEquals(50_000L, retry.payment.amountReceived)
        assertNotEquals(vm.state.value.payment.amountReceived, retry.payment.amountReceived)
    }

    // ---------------------------------------------------------------------
    // 7. payment method frozen across retry
    // ---------------------------------------------------------------------
    @Test
    fun paymentMethod_isFrozenAcrossRetryEvenWhenUiMethodChanges() = runTest(dispatcher) {
        seedCart()
        val handle = freshHandle()
        val vm = viewModel(handle)
        runCurrent()

        heldOrders.checkoutResult = Result.failure(IOException("lost response"))
        vm.selectQuickTender(50_000)
        vm.confirmPayment()
        advanceUntilIdle()

        // Cashier switches the UI to QRIS after the unresolved outcome.
        vm.selectPaymentMethod(CheckoutPaymentMethod.QRIS)
        assertEquals(CheckoutPaymentMethod.QRIS, vm.state.value.payment.method)

        heldOrders.checkoutResult = Result.success(
            HeldOrderCheckoutResult(canonicalTransaction(), replayed = false),
        )
        vm.retryHeldOrderCheckout()
        advanceUntilIdle()

        assertEquals("CASH", heldOrders.checkoutCalls.last().payment.method)
    }

    // ---------------------------------------------------------------------
    // 8. QRIS retry stays QRIS with no cash amount
    // ---------------------------------------------------------------------
    @Test
    fun qrisRetry_staysQrisWithNoCashAmount() = runTest(dispatcher) {
        seedCart()
        val handle = freshHandle()
        val vm = viewModel(handle)
        runCurrent()

        heldOrders.checkoutResult = Result.failure(IOException("lost response"))
        vm.selectPaymentMethod(CheckoutPaymentMethod.QRIS)
        vm.confirmPayment()
        advanceUntilIdle()

        assertEquals("QRIS", heldOrders.checkoutCalls.first().payment.method)
        assertNull(heldOrders.checkoutCalls.first().payment.amountReceived)

        // UI switched to CASH; the frozen QRIS payload must still be replayed.
        vm.selectPaymentMethod(CheckoutPaymentMethod.CASH)
        vm.selectQuickTender(100_000)

        heldOrders.checkoutResult = Result.success(
            HeldOrderCheckoutResult(canonicalTransaction(), replayed = false),
        )
        vm.retryHeldOrderCheckout()
        advanceUntilIdle()

        val retry = heldOrders.checkoutCalls.last()
        assertEquals("QRIS", retry.payment.method)
        assertNull(retry.payment.amountReceived)
    }

    // ---------------------------------------------------------------------
    // 9. repeated IOException keeps identical pending request
    // ---------------------------------------------------------------------
    @Test
    fun repeatedIOException_keepsIdenticalPendingRequest() = runTest(dispatcher) {
        seedCart()
        val handle = freshHandle()
        val vm = viewModel(handle)
        runCurrent()

        heldOrders.checkoutResult = Result.failure(IOException("timeout 1"))
        vm.selectQuickTender(50_000)
        vm.confirmPayment()
        advanceUntilIdle()

        val afterFirst = PendingHeldOrderCheckoutContext(handle).getPending()

        heldOrders.checkoutResult = Result.failure(IOException("timeout 2"))
        vm.retryHeldOrderCheckout()
        advanceUntilIdle()

        heldOrders.checkoutResult = Result.failure(IOException("timeout 3"))
        vm.retryHeldOrderCheckout()
        advanceUntilIdle()

        assertEquals(3, heldOrders.checkoutCalls.size)
        assertEquals(heldOrders.checkoutCalls[0], heldOrders.checkoutCalls[1])
        assertEquals(heldOrders.checkoutCalls[1], heldOrders.checkoutCalls[2])
        assertEquals(afterFirst, PendingHeldOrderCheckoutContext(handle).getPending())
        assertTrue(vm.state.value.reconciliationNeeded)
        assertFalse(vm.state.value.isLoading)
        assertEquals(1, cart.getCart().value.items.size)
        assertNotNull(cart.getHeldOrderIdentity().value)
        assertEquals(0, offlineQueue.queueCalls.size)
        assertEquals(0, printUseCase.invocations.size)
    }

    // ---------------------------------------------------------------------
    // 10. process recreation restores pending request
    // ---------------------------------------------------------------------
    @Test
    fun processRecreation_restoresPendingRequest() = runTest(dispatcher) {
        seedCart()
        val handle = freshHandle()
        val vm = viewModel(handle)
        runCurrent()

        heldOrders.checkoutResult = Result.failure(IOException("lost response"))
        vm.selectQuickTender(50_000)
        vm.confirmPayment()
        advanceUntilIdle()

        // Same SavedStateHandle, brand new ViewModel == recreated process.
        val recreated = viewModel(handle)
        runCurrent()

        assertTrue(recreated.state.value.reconciliationNeeded)
        assertNotNull(recreated.state.value.reconciliationMessage)

        val restored = PendingHeldOrderCheckoutContext(handle).getPending()
        assertNotNull(restored)
        assertEquals(heldOrderId, restored!!.heldOrderId)
        assertEquals(expectedVersion, restored.expectedVersion)
        assertEquals(clientTxId, restored.clientTransactionId)
        assertEquals("CASH", restored.paymentMethod)
        assertEquals(50_000L, restored.amountReceived)
    }

    // ---------------------------------------------------------------------
    // 11. process recreation retry uses same clientTransactionId/payment
    // ---------------------------------------------------------------------
    @Test
    fun processRecreation_retryUsesSameClientTxIdAndPayment() = runTest(dispatcher) {
        seedCart()
        val handle = freshHandle()
        val vm = viewModel(handle)
        runCurrent()

        heldOrders.checkoutResult = Result.failure(IOException("lost response"))
        vm.selectQuickTender(50_000)
        vm.confirmPayment()
        advanceUntilIdle()

        // Cashier edits payment in the recreated process before retrying.
        val recreated = viewModel(handle)
        runCurrent()
        recreated.selectQuickTender(100_000)

        heldOrders.checkoutResult = Result.success(
            HeldOrderCheckoutResult(canonicalTransaction(), replayed = true),
        )
        recreated.retryHeldOrderCheckout()
        advanceUntilIdle()

        assertEquals(2, heldOrders.checkoutCalls.size)
        assertEquals(heldOrders.checkoutCalls[0], heldOrders.checkoutCalls[1])
        assertEquals(clientTxId, heldOrders.checkoutCalls[1].clientTransactionId)
        assertEquals(50_000L, heldOrders.checkoutCalls[1].payment.amountReceived)
        // No new identity was minted.
        assertEquals(clientTxId, handle.get<String>(CheckoutTransactionIdentity.KEY))
    }

    // ---------------------------------------------------------------------
    // 12. attached identity mismatch after recreation prevents API call
    // ---------------------------------------------------------------------
    @Test
    fun attachedIdentityMismatch_afterRecreationPreventsApiCall() = runTest(dispatcher) {
        seedCart()
        val handle = freshHandle()
        val vm = viewModel(handle)
        runCurrent()

        heldOrders.checkoutResult = Result.failure(IOException("lost response"))
        vm.selectQuickTender(50_000)
        vm.confirmPayment()
        advanceUntilIdle()

        // A different held order gets attached while reconciliation is pending.
        cart.detachHeldOrderIdentity()
        cart.attachHeldOrderIdentity(heldOrderId = "held-order-OTHER", expectedVersion = 1, label = null)

        val recreated = viewModel(handle)
        runCurrent()
        recreated.retryHeldOrderCheckout()
        advanceUntilIdle()

        assertEquals(1, heldOrders.checkoutCalls.size)
        assertEquals(heldOrderId, heldOrders.checkoutCalls.single().id)
        assertNotEquals("held-order-OTHER", heldOrders.checkoutCalls.single().id)
        assertNotNull(recreated.state.value.errorMessage)
        assertTrue(recreated.state.value.reconciliationNeeded)
        // Safe state preserved: pending metadata and attached identity untouched.
        assertEquals(heldOrderId, PendingHeldOrderCheckoutContext(handle).getPending()?.heldOrderId)
        assertEquals("held-order-OTHER", cart.getHeldOrderIdentity().value?.heldOrderId)
        assertEquals(0, printUseCase.invocations.size)
    }

    // ---------------------------------------------------------------------
    // 13. retry replayed=true: full success lifecycle, no printer/drawer
    // ---------------------------------------------------------------------
    @Test
    fun retryReplayedTrue_clearsPendingCartAndIdentityWithNoHardware() = runTest(dispatcher) {
        seedCart()
        val handle = freshHandle()
        val vm = viewModel(handle)
        runCurrent()

        heldOrders.checkoutResult = Result.failure(IOException("lost response"))
        vm.selectQuickTender(50_000)
        vm.confirmPayment()
        advanceUntilIdle()

        heldOrders.checkoutResult = Result.success(
            HeldOrderCheckoutResult(canonicalTransaction(), replayed = true),
        )
        vm.retryHeldOrderCheckout()
        advanceUntilIdle()

        assertFalse(vm.state.value.reconciliationNeeded)
        assertNull(vm.state.value.reconciliationMessage)
        assertNull(vm.state.value.errorMessage)
        assertEquals("server-tx-1", vm.state.value.transaction?.id)
        assertEquals(true, vm.state.value.heldOrderReplayed)

        assertTrue(cart.getCart().value.items.isEmpty())
        assertNull(cart.getHeldOrderIdentity().value)
        assertNull(PendingHeldOrderCheckoutContext(handle).getPending())
        assertFalse(PendingHeldOrderCheckoutContext(handle).reconciliationNeeded)

        assertEquals(0, receipts.requestedIds.size)
        assertEquals(0, printUseCase.invocations.size)
    }

    // ---------------------------------------------------------------------
    // 14. retry replayed=false: same lifecycle, no printer/drawer
    // ---------------------------------------------------------------------
    @Test
    fun retryReplayedFalse_clearsPendingCartAndIdentityWithNoHardware() = runTest(dispatcher) {
        seedCart()
        val handle = freshHandle()
        val vm = viewModel(handle)
        runCurrent()

        heldOrders.checkoutResult = Result.failure(IOException("lost response"))
        vm.selectQuickTender(50_000)
        vm.confirmPayment()
        advanceUntilIdle()

        heldOrders.checkoutResult = Result.success(
            HeldOrderCheckoutResult(canonicalTransaction(), replayed = false),
        )
        vm.retryHeldOrderCheckout()
        advanceUntilIdle()

        assertFalse(vm.state.value.reconciliationNeeded)
        assertNull(PendingHeldOrderCheckoutContext(handle).getPending())
        assertEquals("server-tx-1", vm.state.value.transaction?.id)
        assertEquals(false, vm.state.value.heldOrderReplayed)
        assertTrue(cart.getCart().value.items.isEmpty())
        assertNull(cart.getHeldOrderIdentity().value)
        assertEquals(0, printUseCase.invocations.size)
    }

    // ---------------------------------------------------------------------
    // 15. initial immediate success clears pending metadata
    // ---------------------------------------------------------------------
    @Test
    fun initialSuccess_clearsPendingMetadata() = runTest(dispatcher) {
        seedCart()
        val handle = freshHandle()
        val vm = viewModel(handle)
        runCurrent()

        heldOrders.checkoutResult = Result.success(
            HeldOrderCheckoutResult(canonicalTransaction(), replayed = false),
        )
        vm.selectQuickTender(50_000)
        vm.confirmPayment()
        advanceUntilIdle()

        assertEquals(1, heldOrders.checkoutCalls.size)
        assertFalse(vm.state.value.reconciliationNeeded)
        assertNull(PendingHeldOrderCheckoutContext(handle).getPending())
        assertFalse(PendingHeldOrderCheckoutContext(handle).reconciliationNeeded)
        assertNull(PendingHeldOrderCheckoutContext(handle).clientTransactionId)
        assertEquals("server-tx-1", vm.state.value.transaction?.id)
        assertEquals(0, printUseCase.invocations.size)
    }

    // ---------------------------------------------------------------------
    // 16. deterministic initial error does not become unknown-outcome
    // ---------------------------------------------------------------------
    @Test
    fun deterministicInitialError_isNotTreatedAsUnknownOutcome() = runTest(dispatcher) {
        seedCart()
        val handle = freshHandle()
        val vm = viewModel(handle)
        runCurrent()

        heldOrders.checkoutResult = Result.failure(
            HeldOrderError(
                httpCode = 409,
                errorCode = "HELD_ORDER_VERSION_CONFLICT",
                message = "Held order version is stale",
            ),
        )
        vm.selectQuickTender(50_000)
        vm.confirmPayment()
        advanceUntilIdle()

        assertFalse(vm.state.value.reconciliationNeeded)
        assertNull(vm.state.value.reconciliationMessage)
        assertEquals("Held order version is stale", vm.state.value.errorMessage)
        assertNull(PendingHeldOrderCheckoutContext(handle).getPending())
        assertFalse(PendingHeldOrderCheckoutContext(handle).reconciliationNeeded)
        assertEquals(1, cart.getCart().value.items.size)
        assertNotNull(cart.getHeldOrderIdentity().value)
        assertEquals(0, offlineQueue.queueCalls.size)
        assertEquals(0, printUseCase.invocations.size)

        // The cashier can still correct and resubmit a brand new checkout.
        heldOrders.checkoutResult = Result.success(
            HeldOrderCheckoutResult(canonicalTransaction(), replayed = false),
        )
        vm.confirmPayment()
        advanceUntilIdle()
        assertEquals(2, heldOrders.checkoutCalls.size)
    }

    // ---------------------------------------------------------------------
    // 17. IDEMPOTENCY_PAYLOAD_MISMATCH during reconciliation
    // ---------------------------------------------------------------------
    @Test
    fun idempotencyPayloadMismatchDuringReconciliation_keepsFrozenStateAndNoHardware() = runTest(dispatcher) {
        seedCart()
        val handle = freshHandle()
        val vm = viewModel(handle)
        runCurrent()

        heldOrders.checkoutResult = Result.failure(IOException("lost response"))
        vm.selectQuickTender(50_000)
        vm.confirmPayment()
        advanceUntilIdle()

        val pendingBefore = PendingHeldOrderCheckoutContext(handle).getPending()

        heldOrders.checkoutResult = Result.failure(
            HeldOrderError(
                httpCode = 409,
                errorCode = "IDEMPOTENCY_PAYLOAD_MISMATCH",
                message = "Idempotency key reused with a different payload",
            ),
        )
        vm.retryHeldOrderCheckout()
        advanceUntilIdle()

        assertEquals(2, heldOrders.checkoutCalls.size)
        // No new client transaction id and no different payment under the same id.
        assertEquals(heldOrders.checkoutCalls[0], heldOrders.checkoutCalls[1])
        assertEquals(clientTxId, heldOrders.checkoutCalls[1].clientTransactionId)
        assertEquals(clientTxId, handle.get<String>(CheckoutTransactionIdentity.KEY))

        // Cart and identity preserved, pending metadata retained for investigation.
        assertEquals(1, cart.getCart().value.items.size)
        assertEquals(heldOrderId, cart.getHeldOrderIdentity().value?.heldOrderId)
        assertEquals(pendingBefore, PendingHeldOrderCheckoutContext(handle).getPending())
        assertTrue(vm.state.value.reconciliationNeeded)
        assertNotNull(vm.state.value.errorMessage)

        assertEquals(0, offlineQueue.queueCalls.size)
        assertEquals(0, printUseCase.invocations.size)
        assertEquals(0, receipts.requestedIds.size)
    }

    // ---------------------------------------------------------------------
    // 18. HELD_ORDER_NOT_OPEN during reconciliation
    // ---------------------------------------------------------------------
    @Test
    fun heldOrderNotOpenDuringReconciliation_keepsSafeReconciliationState() = runTest(dispatcher) {
        seedCart()
        val handle = freshHandle()
        val vm = viewModel(handle)
        runCurrent()

        heldOrders.checkoutResult = Result.failure(IOException("lost response"))
        vm.selectQuickTender(50_000)
        vm.confirmPayment()
        advanceUntilIdle()

        val pendingBefore = PendingHeldOrderCheckoutContext(handle).getPending()

        heldOrders.checkoutResult = Result.failure(
            HeldOrderError(
                httpCode = 409,
                errorCode = "HELD_ORDER_NOT_OPEN",
                message = "Held order is not open",
            ),
        )
        vm.retryHeldOrderCheckout()
        advanceUntilIdle()

        assertEquals(2, heldOrders.checkoutCalls.size)
        // No new transaction attempt under a different id.
        assertEquals(heldOrders.checkoutCalls[0], heldOrders.checkoutCalls[1])
        assertEquals(pendingBefore, PendingHeldOrderCheckoutContext(handle).getPending())
        // Must not assume "safe to start over".
        assertTrue(vm.state.value.reconciliationNeeded)
        assertEquals(1, cart.getCart().value.items.size)
        assertEquals(heldOrderId, cart.getHeldOrderIdentity().value?.heldOrderId)
        assertEquals(0, printUseCase.invocations.size)
    }

    // ---------------------------------------------------------------------
    // 19. duplicate reconciliation taps -> exactly one API call
    // ---------------------------------------------------------------------
    @Test
    fun duplicateReconciliationTaps_issueExactlyOneApiCall() = runTest(dispatcher) {
        seedCart()
        val handle = freshHandle()
        val vm = viewModel(handle)
        runCurrent()

        heldOrders.checkoutResult = Result.failure(IOException("lost response"))
        heldOrders.suspendUntilComplete = true
        vm.selectQuickTender(50_000)
        vm.confirmPayment()
        runCurrent()

        assertEquals(1, heldOrders.checkoutCalls.size)
        heldOrders.complete(Result.failure(IOException("lost response")))
        advanceUntilIdle()
        assertTrue(vm.state.value.reconciliationNeeded)

        // Now hammer the explicit retry action.
        heldOrders.suspendUntilComplete = true
        vm.retryHeldOrderCheckout()
        vm.retryHeldOrderCheckout()
        vm.retryHeldOrderCheckout()
        runCurrent()

        assertEquals(2, heldOrders.checkoutCalls.size)
        heldOrders.complete(Result.success(HeldOrderCheckoutResult(canonicalTransaction(), replayed = true)))
        advanceUntilIdle()

        assertEquals(2, heldOrders.checkoutCalls.size)
        assertEquals("server-tx-1", vm.state.value.transaction?.id)
    }

    // ---------------------------------------------------------------------
    // 20. normal checkout regression remains unchanged
    // ---------------------------------------------------------------------
    @Test
    fun normalCheckoutRegression_isUnchangedByPendingHeldOrderState() = runTest(dispatcher) {
        // Seed a cart without any held order attachment at all.
        val product = Product(
            id = "prod-1",
            tenantId = "tenant-1",
            categoryId = "cat-1",
            name = "Latte",
            sku = "LT",
            price = cartTotal,
            cost = 10_000,
            minimumStock = 0,
            trackStock = false,
            isActive = true,
            createdAt = "now",
        )
        cart.addProduct(product)
        cart.addProduct(product)

        val handle = freshHandle()
        val vm = viewModel(handle)
        runCurrent()

        vm.selectQuickTender(cartTotal * 2)
        vm.confirmPayment()
        advanceUntilIdle()

        if (transactions.createV1Calls.isEmpty()) {
            println("VM error: ${vm.state.value.errorMessage}, payment: ${vm.state.value.payment}, cart: ${vm.state.value.cart}")
        }

        assertEquals(1, transactions.createV1Calls.size)
        assertEquals(0, heldOrders.checkoutCalls.size)
        assertEquals(clientTxId, transactions.createV1Calls.single().clientTransactionId)
        assertEquals("server-tx-1", vm.state.value.transaction?.id)
        assertTrue(cart.getCart().value.items.isEmpty())
        assertNull(PendingHeldOrderCheckoutContext(handle).getPending())
        assertFalse(vm.state.value.reconciliationNeeded)
    }

    @Test
    fun normalCheckoutOfflineFallback_isUnchangedByPendingHeldOrderState() = runTest(dispatcher) {
        val product = Product(
            id = "prod-1",
            tenantId = "tenant-1",
            categoryId = "cat-1",
            name = "Latte",
            sku = "LT",
            price = cartTotal,
            cost = 10_000,
            minimumStock = 0,
            trackStock = false,
            isActive = true,
            createdAt = "now",
        )
        cart.addProduct(product)

        val handle = freshHandle()
        val vm = viewModel(handle)
        runCurrent()

        transactions.result = Result.failure(IOException("offline"))
        vm.selectQuickTender(cartTotal)
        vm.confirmPayment()
        advanceUntilIdle()

        assertEquals(1, transactions.createV1Calls.size)
        assertEquals(1, offlineQueue.queueCalls.size)
        assertEquals(0, heldOrders.checkoutCalls.size)
        assertEquals(clientTxId, vm.state.value.offlineQueuedClientTransactionId)
        assertFalse(vm.state.value.reconciliationNeeded)
    }

    // --- Fakes ---

    private class RecordingHeldOrderRepository : HeldOrderRepository {
        val checkoutCalls = mutableListOf<HeldOrderCheckoutRequest>()
        var checkoutResult: Result<HeldOrderCheckoutResult> = Result.failure(IOException("not configured"))
        var suspendUntilComplete = false
        var onCall: ((HeldOrderCheckoutRequest) -> Unit)? = null
        private var pendingDeferred: CompletableDeferred<Result<HeldOrderCheckoutResult>>? = null

        override suspend fun checkout(request: HeldOrderCheckoutRequest): Result<HeldOrderCheckoutResult> {
            checkoutCalls += request
            onCall?.invoke(request)
            if (suspendUntilComplete) {
                val deferred = CompletableDeferred<Result<HeldOrderCheckoutResult>>()
                pendingDeferred = deferred
                return deferred.await()
            }
            return checkoutResult
        }

        fun complete(result: Result<HeldOrderCheckoutResult>) {
            pendingDeferred?.complete(result)
        }

        override suspend fun create(request: HeldOrderCreateRequest): Result<HeldOrder> = error("Unused")
        override suspend fun list(outletId: String?, status: String, page: Int, limit: Int): Result<List<HeldOrder>> = error("Unused")
        override suspend fun get(id: String): Result<HeldOrder> = error("Unused")
        override suspend fun update(request: HeldOrderUpdateRequest): Result<HeldOrder> = error("Unused")
        override suspend fun cancel(id: String, expectedVersion: Int): Result<HeldOrder> = error("Unused")
    }

    private class RecordingTransactionRepository : TransactionRepository {
        val createV1Calls = mutableListOf<V1TransactionRequest>()
        var result: Result<Transaction> = Result.success(
            Transaction(
                id = "server-tx-1",
                clientTransactionId = "client-tx-1",
                outletId = "outlet-1",
                userId = "cashier-1",
                customerId = null,
                status = "COMPLETED",
                subtotal = 50_000,
                discount = 0,
                tax = 0,
                total = 50_000,
                items = emptyList(),
                payments = emptyList(),
                change = 0,
                createdAt = "now",
            ),
        )

        override suspend fun createV1Transaction(request: V1TransactionRequest): Result<Transaction> {
            createV1Calls += request
            return result
        }

        override suspend fun getTransactions(from: String?, to: String?): Result<List<Transaction>> = error("Unused")
        override suspend fun getTransactionDetail(transactionId: String): Result<Transaction> = error("Unused")
        override suspend fun createTransaction(
            clientTransactionId: String,
            outletId: String,
            customerId: String?,
            items: List<CartItem>,
            paymentAmount: Long,
        ): Result<Transaction> = error("Unused")
    }

    private class RecordingOfflineQueue : OfflineSyncRepository {
        val queueCalls = mutableListOf<V1TransactionRequest>()
        override suspend fun queueV1Transaction(
            request: V1TransactionRequest,
            financialSnapshot: OfflineFinancialSnapshot,
        ): Result<OfflineTransaction> {
            queueCalls += request
            return Result.success(
                OfflineTransaction(
                    "row", "tenant-1", "cashier-1", request.clientTransactionId,
                    request.outletId, "{}", "PENDING", null, null, 0, 0, 0,
                ),
            )
        }
        override suspend fun queueTransaction(clientTransactionId: String, outletId: String, customerId: String?, items: List<CartItem>, paymentAmount: Long, financialSnapshot: OfflineFinancialSnapshot): Result<OfflineTransaction> = error("Unused")
        override suspend fun getPendingTransactions(limit: Int): Result<List<OfflineTransaction>> = error("Unused")
        override suspend fun getTransaction(clientTransactionId: String): Result<OfflineTransaction?> = error("Unused")
        override suspend fun getFailedTransactions(limit: Int): Result<List<OfflineTransaction>> = error("Unused")
        override suspend fun getActionRequiredTransactions(limit: Int): Result<List<OfflineTransaction>> = error("Unused")
        override suspend fun getRecoveryTransactions(limit: Int): Result<List<OfflineTransaction>> = error("Unused")
        override fun observeQueueSummary(): Flow<OfflineQueueSummary> = emptyFlow()
        override fun observePendingCount(): Flow<Int> = emptyFlow()
        override suspend fun syncPendingTransactions(): Result<SyncOutcome> = error("Unused")
        override suspend fun syncPendingTransactionsForAccount(tenantId: String, userId: String): Result<SyncOutcome> = error("Unused")
        override suspend fun retryFailedTransaction(clientTransactionId: String): Result<SyncOutcome> = error("Unused")
        override suspend fun deleteFailedTransaction(clientTransactionId: String): Result<Unit> = error("Unused")
        override suspend fun acknowledgeReconciliation(clientTransactionId: String): Result<Unit> = error("Unused")
        override suspend fun retryFailedTransactions(): Result<SyncOutcome> = error("Unused")
    }

    private class FakeOutlet(outlet: Outlet?) : OutletRepository {
        override val selectedOutlet: StateFlow<Outlet?> = MutableStateFlow(outlet)
        override suspend fun getOutlets(): Result<List<Outlet>> = error("Unused")
        override suspend fun selectOutlet(outlet: Outlet) = error("Unused")
        override suspend fun clearSelectedOutlet(tenantId: String?, userId: String?) = error("Unused")
        override suspend fun restoreSelectedOutlet() = error("Unused")
    }

    private class FakeShift(shift: Shift?) : ShiftRepository {
        override val currentShift: StateFlow<Shift?> = MutableStateFlow(shift)
        override suspend fun getCurrentShift(): Result<Shift?> = error("Unused")
        override suspend fun openShift(outletId: String): Result<Shift> = error("Unused")
        override suspend fun closeShift(shiftId: String): Result<Shift> = error("Unused")
        override suspend fun getShiftSummary(shiftId: String): Result<ShiftSummary> = error("Unused")
        override suspend fun clearCurrentShift(tenantId: String?, userId: String?) = error("Unused")
        override suspend fun restoreCurrentShift(expectedOutletId: String?) = error("Unused")
    }

    private class RecordingReceiptRepository : ReceiptRepository {
        val requestedIds = mutableListOf<String>()
        override suspend fun getReceipt(transactionId: String): Result<Receipt> {
            requestedIds += transactionId
            return Result.success(
                Receipt(
                    transactionId = transactionId,
                    clientTransactionId = "client-tx-1",
                    status = "COMPLETED",
                    createdAt = "now",
                    tenant = ReceiptTenant("tenant-1", "Tenant", null),
                    outlet = ReceiptOutlet("outlet-1", "Main", null),
                    cashier = ReceiptCashier("cashier-1", "Cashier"),
                    customer = null,
                    items = emptyList(),
                    payment = Payment("p1", "CASH", "COMPLETED", 50_000, "now", 50_000, 0),
                    subtotal = 50_000,
                    discount = 0,
                    tax = 0,
                    total = 50_000,
                    change = 0,
                ),
            )
        }
    }

    private class RecordingPrintAfterCheckoutUseCase : PrintAfterCheckoutUseCase() {
        data class Invocation(
            val receipt: Receipt,
            val paymentMethod: String,
            val isOriginalOnlineCheckout: Boolean,
        )
        val invocations = mutableListOf<Invocation>()
        override suspend fun invoke(receipt: Receipt, paymentMethod: String, isOriginalOnlineCheckout: Boolean): Result {
            invocations += Invocation(receipt, paymentMethod, isOriginalOnlineCheckout)
            return Result.Success(printed = true, drawerOpened = true)
        }
    }
}