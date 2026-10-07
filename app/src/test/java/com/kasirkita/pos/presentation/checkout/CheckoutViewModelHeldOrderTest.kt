package com.kasirkita.pos.presentation.checkout

import androidx.lifecycle.SavedStateHandle
import com.kasirkita.pos.data.repository.CartRepositoryImpl
import com.kasirkita.pos.domain.error.HeldOrderError
import com.kasirkita.pos.domain.model.*
import com.kasirkita.pos.domain.repository.*
import com.kasirkita.pos.domain.usecase.GetReceiptUseCase
import com.kasirkita.pos.domain.usecase.PrintAfterCheckoutUseCase
import com.kasirkita.pos.domain.usecase.QueueOfflineTransactionUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelHeldOrderTest {
    private val dispatcher = StandardTestDispatcher()
    private val cart = CartRepositoryImpl()
    private val transactions = RecordingTransactionRepository()
    private val offlineQueue = RecordingOfflineQueue()
    private val heldOrders = RecordingHeldOrderRepository()
    private val receipts = RecordingReceiptRepository()
    private val printUseCase = RecordingPrintAfterCheckoutUseCase()
    private val defaultOutlet = Outlet("outlet-1", "tenant-1", "Main", null, true, "now")
    private val defaultShift = Shift("session-1", defaultOutlet.id, "cashier-1", null, null, null, null, "OPEN", "now", null)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun seedCart(price: Long = 20_000, quantity: Int = 1) {
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
    }

    private fun createViewModel(
        savedClientTransactionId: String? = "saved-client-tx-1",
        outlet: Outlet? = defaultOutlet,
        shift: Shift? = defaultShift,
    ): CheckoutViewModel {
        val handle = if (savedClientTransactionId != null) {
            SavedStateHandle(mapOf(CheckoutTransactionIdentity.KEY to savedClientTransactionId))
        } else {
            SavedStateHandle()
        }
        return CheckoutViewModel(
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
    }

    private fun canonicalTransaction(clientTxId: String = "saved-client-tx-1") = Transaction(
        id = "server-tx-123",
        clientTransactionId = clientTxId,
        outletId = defaultOutlet.id,
        userId = "cashier-1",
        customerId = null,
        cashierSessionId = defaultShift.id,
        shiftId = defaultShift.id,
        status = "COMPLETED",
        subtotal = 20_000,
        discount = 0,
        tax = 0,
        total = 20_000,
        items = emptyList(),
        payments = listOf(
            Payment("pay-1", "CASH", "COMPLETED", 20_000, "now", 20_000, 0),
        ),
        change = 0,
        createdAt = "now",
    )

    // 1. Attached Held Order CASH: calls HeldOrderRepository.checkout exactly once
    @Test
    fun attachedHeldOrderCash_callsHeldOrderRepositoryCheckoutExactlyOnce() = runTest(dispatcher) {
        seedCart(price = 20_000)
        cart.attachHeldOrderIdentity(heldOrderId = "held-42", expectedVersion = 3, label = "Table 5")
        heldOrders.checkoutResult = Result.success(HeldOrderCheckoutResult(canonicalTransaction(), replayed = false))

        val vm = createViewModel()
        runCurrent()

        vm.selectPaymentMethod(CheckoutPaymentMethod.CASH)
        vm.selectQuickTender(20_000)
        vm.confirmPayment()
        advanceUntilIdle()

        assertEquals(1, heldOrders.checkoutCalls.size)
        assertEquals(0, transactions.createV1Calls.size)
        assertEquals(0, offlineQueue.queueCalls.size)
    }

    // 2. Attached Held Order QRIS: calls HeldOrderRepository.checkout exactly once
    @Test
    fun attachedHeldOrderQris_callsHeldOrderRepositoryCheckoutExactlyOnce() = runTest(dispatcher) {
        seedCart(price = 20_000)
        cart.attachHeldOrderIdentity(heldOrderId = "held-42", expectedVersion = 3, label = "Table 5")
        heldOrders.checkoutResult = Result.success(HeldOrderCheckoutResult(canonicalTransaction(), replayed = false))

        val vm = createViewModel()
        runCurrent()

        vm.selectPaymentMethod(CheckoutPaymentMethod.QRIS)
        vm.confirmPayment()
        advanceUntilIdle()

        assertEquals(1, heldOrders.checkoutCalls.size)
        assertEquals(0, transactions.createV1Calls.size)
        assertEquals(0, offlineQueue.queueCalls.size)
    }

    // 3. Request contains correct heldOrderId, expectedVersion, stable clientTransactionId, payment
    @Test
    fun requestContainsCorrectIdVersionStableClientTxIdAndPayment() = runTest(dispatcher) {
        seedCart(price = 20_000)
        cart.attachHeldOrderIdentity(heldOrderId = "held-99", expectedVersion = 7, label = null)
        heldOrders.checkoutResult = Result.success(HeldOrderCheckoutResult(canonicalTransaction("client-id-abc"), replayed = false))

        val vm = createViewModel(savedClientTransactionId = "client-id-abc")
        runCurrent()

        vm.selectPaymentMethod(CheckoutPaymentMethod.CASH)
        vm.selectQuickTender(50_000)
        vm.confirmPayment()
        advanceUntilIdle()

        val req = heldOrders.checkoutCalls.single()
        assertEquals("held-99", req.id)
        assertEquals(7, req.expectedVersion)
        assertEquals("client-id-abc", req.clientTransactionId)
        assertEquals("CASH", req.payment.method)
        assertEquals(50_000L, req.payment.amountReceived)
    }

    // 4. CASH amountReceived parity
    @Test
    fun cashAmountReceivedParity_forwardedExactlyToPayment() = runTest(dispatcher) {
        seedCart(price = 20_000)
        cart.attachHeldOrderIdentity(heldOrderId = "held-1", expectedVersion = 1, label = null)
        heldOrders.checkoutResult = Result.success(HeldOrderCheckoutResult(canonicalTransaction(), replayed = false))

        val vm = createViewModel()
        runCurrent()

        vm.selectPaymentMethod(CheckoutPaymentMethod.CASH)
        vm.selectQuickTender(100_000)
        vm.confirmPayment()
        advanceUntilIdle()

        val req = heldOrders.checkoutCalls.single()
        assertEquals("CASH", req.payment.method)
        assertEquals(100_000L, req.payment.amountReceived)
    }

    // 5. QRIS contains no cash-only amountReceived
    @Test
    fun qrisContainsNoCashOnlyAmountReceived() = runTest(dispatcher) {
        seedCart(price = 20_000)
        cart.attachHeldOrderIdentity(heldOrderId = "held-1", expectedVersion = 1, label = null)
        heldOrders.checkoutResult = Result.success(HeldOrderCheckoutResult(canonicalTransaction(), replayed = false))

        val vm = createViewModel()
        runCurrent()

        vm.selectPaymentMethod(CheckoutPaymentMethod.QRIS)
        vm.confirmPayment()
        advanceUntilIdle()

        val req = heldOrders.checkoutCalls.single()
        assertEquals("QRIS", req.payment.method)
        assertNull(req.payment.amountReceived)
    }

    // 6. Successful original response: canonical tx, identity detached, cart cleared, no offline, no print, no drawer
    @Test
    fun successfulOriginalResponse_fullS1SuccessLifecycle() = runTest(dispatcher) {
        seedCart(price = 20_000)
        cart.attachHeldOrderIdentity(heldOrderId = "held-1", expectedVersion = 1, label = null)
        val serverTx = canonicalTransaction()
        heldOrders.checkoutResult = Result.success(HeldOrderCheckoutResult(serverTx, replayed = false))

        val vm = createViewModel()
        runCurrent()

        vm.selectPaymentMethod(CheckoutPaymentMethod.CASH)
        vm.selectQuickTender(20_000)
        vm.confirmPayment()
        advanceUntilIdle()

        // 1. canonical transaction exposed
        assertEquals(serverTx, vm.state.value.transaction)
        assertEquals(false, vm.state.value.heldOrderReplayed)
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.errorMessage)
        assertNull(vm.state.value.printerWarning)

        // 2. identity detached
        assertNull(cart.getHeldOrderIdentity().value)
        assertFalse(cart.isAttachedToHeldOrder())

        // 3. cart cleared
        assertTrue(cart.getCart().value.items.isEmpty())

        // 4. no offline queue
        assertEquals(0, offlineQueue.queueCalls.size)

        // 5. no auto-print, no drawer
        assertEquals(0, receipts.requestedIds.size)
        assertEquals(0, printUseCase.invocations.size)
    }

    // 7. Successful replay response: same S1 lifecycle, no auto-print, no drawer
    @Test
    fun successfulReplayResponse_sameS1LifecycleWithoutHardwareSideEffects() = runTest(dispatcher) {
        seedCart(price = 20_000)
        cart.attachHeldOrderIdentity(heldOrderId = "held-1", expectedVersion = 1, label = null)
        val serverTx = canonicalTransaction()
        heldOrders.checkoutResult = Result.success(HeldOrderCheckoutResult(serverTx, replayed = true))

        val vm = createViewModel()
        runCurrent()

        vm.selectPaymentMethod(CheckoutPaymentMethod.CASH)
        vm.selectQuickTender(20_000)
        vm.confirmPayment()
        advanceUntilIdle()

        // 1. canonical transaction exposed with replayed=true preserved in state
        assertEquals(serverTx, vm.state.value.transaction)
        assertEquals(true, vm.state.value.heldOrderReplayed)
        assertFalse(vm.state.value.isLoading)

        // 2. identity detached & cart cleared
        assertNull(cart.getHeldOrderIdentity().value)
        assertTrue(cart.getCart().value.items.isEmpty())

        // 3. no offline queue, no print, no drawer
        assertEquals(0, offlineQueue.queueCalls.size)
        assertEquals(0, receipts.requestedIds.size)
        assertEquals(0, printUseCase.invocations.size)
    }

    // 8. Structured HeldOrderError: cart preserved, identity preserved, no offline, no hardware
    @Test
    fun structuredHeldOrderError_preservesCartAndIdentityWithoutHardwareEffects() = runTest(dispatcher) {
        seedCart(price = 20_000)
        cart.attachHeldOrderIdentity(heldOrderId = "held-1", expectedVersion = 1, label = "Table 1")
        heldOrders.checkoutResult = Result.failure(
            HeldOrderError(httpCode = 409, errorCode = "HELD_ORDER_VERSION_CONFLICT", message = "Versi order telah berubah"),
        )

        val vm = createViewModel()
        runCurrent()

        vm.selectPaymentMethod(CheckoutPaymentMethod.CASH)
        vm.selectQuickTender(20_000)
        vm.confirmPayment()
        advanceUntilIdle()

        // 1. Error surfaced
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.transaction)
        assertEquals("Versi order telah berubah", vm.state.value.errorMessage)

        // 2. Cart & identity preserved
        assertEquals(1, cart.getCart().value.items.size)
        assertEquals("held-1", cart.getHeldOrderIdentity().value?.heldOrderId)
        assertEquals(1, cart.getHeldOrderIdentity().value?.expectedVersion)
        assertTrue(cart.isAttachedToHeldOrder())

        // 3. No offline, no hardware
        assertEquals(0, offlineQueue.queueCalls.size)
        assertEquals(0, receipts.requestedIds.size)
        assertEquals(0, printUseCase.invocations.size)
    }

    // 9. IOException: cart preserved, identity preserved, stable clientTransactionId retained, no offline, no printer, no drawer
    @Test
    fun ioException_preservesCartIdentityAndStableClientTxId() = runTest(dispatcher) {
        seedCart(price = 20_000)
        cart.attachHeldOrderIdentity(heldOrderId = "held-1", expectedVersion = 1, label = null)
        heldOrders.checkoutResult = Result.failure(IOException("Connection timed out"))

        val vm = createViewModel(savedClientTransactionId = "client-tx-fixed")
        runCurrent()

        vm.selectPaymentMethod(CheckoutPaymentMethod.CASH)
        vm.selectQuickTender(20_000)
        vm.confirmPayment()
        advanceUntilIdle()

        // 1. Error surfaced, loading reset
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.transaction)
        assertEquals("Connection timed out", vm.state.value.errorMessage)

        // 2. Cart & identity preserved
        assertEquals(1, cart.getCart().value.items.size)
        assertNotNull(cart.getHeldOrderIdentity().value)

        // 3. No offline queue (M16G-S1 must NOT queue offline!)
        assertEquals(0, offlineQueue.queueCalls.size)
        assertEquals(0, receipts.requestedIds.size)
        assertEquals(0, printUseCase.invocations.size)

        // 4. Client transaction id retained on retry
        heldOrders.checkoutResult = Result.success(HeldOrderCheckoutResult(canonicalTransaction("client-tx-fixed"), replayed = false))
        vm.confirmPayment()
        advanceUntilIdle()

        assertEquals(2, heldOrders.checkoutCalls.size)
        assertEquals("client-tx-fixed", heldOrders.checkoutCalls[0].clientTransactionId)
        assertEquals("client-tx-fixed", heldOrders.checkoutCalls[1].clientTransactionId)
        assertEquals(serverTxId("server-tx-123"), vm.state.value.transaction?.id)
    }

    // 10. Invalid/missing session: API not called, cart/identity preserved
    @Test
    fun missingSession_doesNotCallApiAndPreservesCartAndIdentity() = runTest(dispatcher) {
        seedCart(price = 20_000)
        cart.attachHeldOrderIdentity(heldOrderId = "held-1", expectedVersion = 1, label = null)

        val vm = createViewModel(shift = null)
        runCurrent()

        vm.selectPaymentMethod(CheckoutPaymentMethod.CASH)
        vm.selectQuickTender(20_000)
        vm.confirmPayment()
        advanceUntilIdle()

        assertEquals(0, heldOrders.checkoutCalls.size)
        assertEquals(1, cart.getCart().value.items.size)
        assertTrue(cart.isAttachedToHeldOrder())
        assertEquals("Tidak ada shift aktif", vm.state.value.errorMessage)
    }

    @Test
    fun closedSession_doesNotCallApiAndPreservesCartAndIdentity() = runTest(dispatcher) {
        seedCart(price = 20_000)
        cart.attachHeldOrderIdentity(heldOrderId = "held-1", expectedVersion = 1, label = null)
        val closedShift = defaultShift.copy(status = "CLOSED")

        val vm = createViewModel(shift = closedShift)
        runCurrent()

        vm.selectPaymentMethod(CheckoutPaymentMethod.CASH)
        vm.selectQuickTender(20_000)
        vm.confirmPayment()
        advanceUntilIdle()

        assertEquals(0, heldOrders.checkoutCalls.size)
        assertEquals(1, cart.getCart().value.items.size)
        assertTrue(cart.isAttachedToHeldOrder())
        assertEquals("Tidak ada shift aktif", vm.state.value.errorMessage)
    }

    // 11. Outlet/session mismatch: API not called
    @Test
    fun outletSessionMismatch_doesNotCallApi() = runTest(dispatcher) {
        seedCart(price = 20_000)
        cart.attachHeldOrderIdentity(heldOrderId = "held-1", expectedVersion = 1, label = null)
        val mismatchedShift = defaultShift.copy(outletId = "different-outlet-99")

        val vm = createViewModel(shift = mismatchedShift)
        runCurrent()

        vm.selectPaymentMethod(CheckoutPaymentMethod.CASH)
        vm.selectQuickTender(20_000)
        vm.confirmPayment()
        advanceUntilIdle()

        assertEquals(0, heldOrders.checkoutCalls.size)
        assertEquals(1, cart.getCart().value.items.size)
        assertTrue(cart.isAttachedToHeldOrder())
        assertEquals("Shift aktif tidak sesuai dengan outlet yang dipilih", vm.state.value.errorMessage)
    }

    // 12. Duplicate confirm tap: one checkout API call
    @Test
    fun duplicateConfirmTap_issuesExactlyOneCheckoutApiCall() = runTest(dispatcher) {
        seedCart(price = 20_000)
        cart.attachHeldOrderIdentity(heldOrderId = "held-1", expectedVersion = 1, label = null)
        heldOrders.suspendUntilComplete = true

        val vm = createViewModel()
        runCurrent()

        vm.selectPaymentMethod(CheckoutPaymentMethod.CASH)
        vm.selectQuickTender(20_000)

        // Multiple rapid taps
        vm.confirmPayment()
        vm.confirmPayment()
        vm.confirmPayment()

        runCurrent()
        assertEquals(1, heldOrders.checkoutCalls.size)
        assertTrue(vm.state.value.isLoading)

        // Complete the in-flight checkout
        heldOrders.complete(Result.success(HeldOrderCheckoutResult(canonicalTransaction(), replayed = false)))
        advanceUntilIdle()

        assertEquals(1, heldOrders.checkoutCalls.size)
        assertFalse(vm.state.value.isLoading)
        assertNotNull(vm.state.value.transaction)
    }

    // 13. Normal checkout regression: existing normal online/offline/print/drawer behavior unchanged
    @Test
    fun normalCheckoutRegression_preservesOnlinePrintAndDrawerBehavior() = runTest(dispatcher) {
        seedCart(price = 20_000)
        // Ensure no held order attached
        assertFalse(cart.isAttachedToHeldOrder())

        val vm = createViewModel()
        runCurrent()

        vm.selectPaymentMethod(CheckoutPaymentMethod.CASH)
        vm.selectQuickTender(20_000)
        vm.confirmPayment()
        advanceUntilIdle()

        // Routed to TransactionRepository, NOT HeldOrderRepository
        assertEquals(1, transactions.createV1Calls.size)
        assertEquals(0, heldOrders.checkoutCalls.size)
        assertEquals(0, offlineQueue.queueCalls.size)

        // Normal checkout triggers receipt fetch and print/drawer
        assertEquals(listOf("canonical-tx-1"), receipts.requestedIds)
        assertEquals(1, printUseCase.invocations.size)
        assertEquals("CASH", printUseCase.invocations.single().paymentMethod)
        assertTrue(printUseCase.invocations.single().isOriginalOnlineCheckout)
        assertTrue(cart.getCart().value.items.isEmpty())
        assertNotNull(vm.state.value.transaction)
    }

    @Test
    fun normalCheckoutRegression_preservesOfflineQueueFallbackOnIoException() = runTest(dispatcher) {
        seedCart(price = 20_000)
        transactions.result = Result.failure(IOException("Server unreachable"))

        val vm = createViewModel()
        runCurrent()

        vm.selectPaymentMethod(CheckoutPaymentMethod.CASH)
        vm.selectQuickTender(20_000)
        vm.confirmPayment()
        advanceUntilIdle()

        // Routed to offline queue, not HeldOrderRepository
        assertEquals(1, transactions.createV1Calls.size)
        assertEquals(0, heldOrders.checkoutCalls.size)
        assertEquals(1, offlineQueue.queueCalls.size)
        assertEquals("saved-client-tx-1", vm.state.value.offlineQueuedClientTransactionId)
        assertTrue(cart.getCart().value.items.isEmpty())
    }

    private fun serverTxId(id: String) = id

    // --- Fakes ---

    private class RecordingHeldOrderRepository : HeldOrderRepository {
        val checkoutCalls = mutableListOf<HeldOrderCheckoutRequest>()
        var checkoutResult: Result<HeldOrderCheckoutResult> = Result.success(
            HeldOrderCheckoutResult(
                Transaction(
                    id = "stub-tx",
                    clientTransactionId = "client-tx",
                    outletId = "outlet-1",
                    userId = "cashier-1",
                    customerId = null,
                    status = "COMPLETED",
                    subtotal = 0,
                    discount = 0,
                    tax = 0,
                    total = 0,
                    items = emptyList(),
                    payments = emptyList(),
                    change = 0,
                    createdAt = "now",
                ),
                replayed = false,
            ),
        )
        var suspendUntilComplete = false
        private var pendingDeferred: CompletableDeferred<Result<HeldOrderCheckoutResult>>? = null

        override suspend fun checkout(request: HeldOrderCheckoutRequest): Result<HeldOrderCheckoutResult> {
            checkoutCalls += request
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
                id = "canonical-tx-1",
                clientTransactionId = "saved-client-tx-1",
                outletId = "outlet-1",
                userId = "cashier-1",
                customerId = null,
                status = "COMPLETED",
                subtotal = 20_000,
                discount = 0,
                tax = 0,
                total = 20_000,
                items = emptyList(),
                payments = listOf(Payment("p1", "CASH", "COMPLETED", 20_000, "now", 20_000, 0)),
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
        override suspend fun queueV1Transaction(request: V1TransactionRequest, financialSnapshot: OfflineFinancialSnapshot): Result<OfflineTransaction> {
            queueCalls += request
            return Result.success(OfflineTransaction("row", "tenant-1", "cashier-1", request.clientTransactionId, request.outletId, "{}", "PENDING", null, null, 0, 0, 0))
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
                    clientTransactionId = "client-tx",
                    status = "COMPLETED",
                    createdAt = "now",
                    tenant = ReceiptTenant("tenant-1", "Tenant", null),
                    outlet = ReceiptOutlet("outlet-1", "Main", null),
                    cashier = ReceiptCashier("cashier-1", "Cashier"),
                    customer = null,
                    items = emptyList(),
                    payment = Payment("p1", "CASH", "COMPLETED", 20_000, "now", 20_000, 0),
                    subtotal = 20_000,
                    discount = 0,
                    tax = 0,
                    total = 20_000,
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
