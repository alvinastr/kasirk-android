package com.kasirkita.pos.presentation.checkout

import androidx.lifecycle.SavedStateHandle
import com.kasirkita.pos.data.repository.CartRepositoryImpl
import com.kasirkita.pos.domain.model.*
import com.kasirkita.pos.domain.repository.*
import com.kasirkita.pos.domain.model.DrawerPulseProfile
import com.kasirkita.pos.domain.model.Payment
import com.kasirkita.pos.domain.model.PrinterConfig
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.repository.ReceiptRepository
import com.kasirkita.pos.domain.usecase.GetReceiptUseCase
import com.kasirkita.pos.domain.usecase.PrintAfterCheckoutUseCase
import com.kasirkita.pos.domain.usecase.QueueOfflineTransactionUseCase
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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val cart = CartRepositoryImpl()
    private val online = FakeTransactions()
    private val offline = FakeQueue()
    private val receiptRepository = FakeReceiptRepository()
    private val printAfterCheckout = FakePrintAfterCheckoutUseCase()
    private val heldOrders = ConfigurableHeldOrders()
    private val handle = SavedStateHandle(mapOf(CheckoutTransactionIdentity.KEY to "sale-id"))
    private val outlet = Outlet("selected-outlet", "tenant", "Outlet", null, true, "now")
    private val shift = Shift("active-shift", outlet.id, "cashier", null, null, null, null, "OPEN", "now", null)

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        receiptRepository.requestedIds.clear()
        printAfterCheckout.invocations.clear()
        printAfterCheckout.result = PrintAfterCheckoutUseCase.Result.Success(printed = true, drawerOpened = true)
    }
    @After fun teardown() { Dispatchers.resetMain() }

    private fun item() = CartItem(
        lineKey = CartLineKey.from("coffee", listOf("ice", "shot"), "Sedikit es"),
        productId = "coffee", name = "Americano", sku = "AM", basePrice = 20_000,
        quantity = 2,
        modifierSelections = listOf(
            CartModifierSelectionSnapshot("ice", "temperature", "Temperature", "Ice", 0),
            CartModifierSelectionSnapshot("shot", "extras", "Extras", "Shot", 5_000),
        ), note = "Sedikit es",
    )

    private fun viewModel(): CheckoutViewModel {
        // The repository fake uses the production cart implementation; its seeded line is identical to item().
        val product = Product("coffee", "tenant", "category", "Americano", "AM", 20_000, 0, 0, false, true, "now")
        cart.addConfiguredProduct(product, item().modifierSelections, item().note)
        cart.updateQuantity(item().lineKey.value, 2)
        return CheckoutViewModel(
            online,
            QueueOfflineTransactionUseCase(offline),
            cart,
            heldOrders,
            FakeOutlet(outlet),
            FakeShift(shift),
            GetReceiptUseCase(receiptRepository),
            printAfterCheckout,
            handle
        )
    }

    private fun viewModelWithDuplicateConfiguredLines(): CheckoutViewModel {
        val product = Product("coffee", "tenant", "category", "Americano", "AM", 20_000, 0, 0, false, true, "now")
        val ice = listOf(CartModifierSelectionSnapshot("ice", "temperature", "Temperature", "Ice", 0))
        val hot = listOf(CartModifierSelectionSnapshot("hot", "temperature", "Temperature", "Hot", 0))
        cart.addConfiguredProduct(product, ice, "Sedikit es")
        cart.addConfiguredProduct(product, hot, "Tanpa gula")
        return CheckoutViewModel(
            online,
            QueueOfflineTransactionUseCase(offline),
            cart,
            heldOrders,
            FakeOutlet(outlet),
            FakeShift(shift),
            GetReceiptUseCase(receiptRepository),
            printAfterCheckout,
            handle
        )
    }

    @Test fun successfulCheckoutInvokesAutoPrintWithCanonicalReceipt() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        vm.checkout(60_000)
        runCurrent()
        online.complete(Result.success(transaction()))
        advanceUntilIdle()

        assertEquals(listOf("server-id"), receiptRepository.requestedIds)
        assertEquals(1, printAfterCheckout.invocations.size)
        assertEquals("server-id", printAfterCheckout.invocations.single().receipt.transactionId)
        assertEquals("CASH", printAfterCheckout.invocations.single().paymentMethod)
        assertTrue(printAfterCheckout.invocations.single().isOriginalOnlineCheckout)
        assertNull(vm.state.value.printerWarning)
    }

    @Test fun autoPrintFailureKeepsTransactionSuccessfulAndSurfacesWarning() = runTest(dispatcher) {
        printAfterCheckout.result = PrintAfterCheckoutUseCase.Result.Failure(
            transactionSuccessful = true,
            printError = "Printer offline",
            drawerError = null,
        )
        val vm = viewModel()
        runCurrent()
        vm.checkout(60_000)
        runCurrent()
        online.complete(Result.success(transaction()))
        advanceUntilIdle()

        assertEquals("sale-id", vm.state.value.transaction?.clientTransactionId)
        assertEquals("Printer offline", vm.state.value.printerWarning)
        assertNull(vm.state.value.errorMessage)
    }

    @Test fun onlineRequestUsesOutletShiftModifiersNoteAndTenderWithoutEarlyClear() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        vm.checkout(60_000)
        runCurrent()
        val request = online.requests.single()
        assertEquals("selected-outlet", request.outletId)
        assertEquals("active-shift", request.cashierSessionId)
        assertEquals("sale-id", request.clientTransactionId)
        assertEquals("CASH", request.payment.method)
        assertEquals(60_000L, request.payment.amountReceived)
        assertEquals("coffee", request.items.single().productId)
        assertEquals(2, request.items.single().quantity)
        assertEquals(listOf("ice", "shot"), request.items.single().modifierOptionIds)
        assertEquals("Sedikit es", request.items.single().note)
        assertEquals(1, cart.getCart().value.items.size)
        assertEquals(0, online.clearObserved)
        online.complete(Result.success(transaction()))
        advanceUntilIdle()
        assertTrue(cart.getCart().value.items.isEmpty())
        assertEquals("sale-id", vm.state.value.transaction?.clientTransactionId)
    }

    @Test fun networkFailureQueuesIdenticalRequestAndConfiguredFinancialSnapshotThenClears() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        vm.checkout(60_000)
        runCurrent()
        online.complete(Result.failure(IOException("offline")))
        advanceUntilIdle()
        val sent = online.requests.single()
        assertEquals(sent, offline.requests.single())
        assertEquals("sale-id", offline.requests.single().clientTransactionId)
        assertEquals("active-shift", offline.requests.single().cashierSessionId)
        assertEquals(listOf("ice", "shot"), offline.requests.single().items.single().modifierOptionIds)
        assertEquals("Sedikit es", offline.requests.single().items.single().note)
        assertEquals(25_000L, item().price)
        assertEquals(50_000L, item().subtotal())
        assertEquals(50_000L, offline.snapshots.single().subtotal)
        assertEquals(50_000L, offline.snapshots.single().total)
        assertEquals(60_000L, offline.snapshots.single().paymentAmount)
        assertEquals(0L, offline.snapshots.single().discount)
        assertEquals(0L, offline.snapshots.single().tax)
        assertTrue(cart.getCart().value.items.isEmpty())
        assertEquals("sale-id", vm.state.value.offlineQueuedClientTransactionId)
        assertEquals(1, online.requests.size)
    }

    @Test fun failedOnlineAndQueueRetainsCartAndSameSaleIdentityForRetry() = runTest(dispatcher) {
        offline.result = Result.failure(IOException("storage"))
        val vm = viewModel()
        runCurrent()
        vm.selectQuickTender(60_000)
        vm.confirmPayment()
        runCurrent()
        online.complete(Result.failure(IOException("offline")))
        advanceUntilIdle()
        assertEquals(1, cart.getCart().value.items.size)
        assertNull(vm.state.value.offlineQueuedClientTransactionId)
        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.payment.canSubmit)
        assertTrue(vm.state.value.errorMessage.orEmpty().contains("tidak dapat disimpan offline"))
        assertEquals("sale-id", offline.requests.single().clientTransactionId)
        vm.checkout(60_000)
        runCurrent()
        assertEquals(listOf("sale-id", "sale-id"), online.requests.map { it.clientTransactionId })
        online.complete(Result.success(transaction()))
        advanceUntilIdle()
        assertTrue(cart.getCart().value.items.isEmpty())
    }

    @Test fun cashRequestUsesTenderNotChangeAndUnderpaymentNeverSubmits() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()

        vm.enterManualCash("40000")
        vm.confirmPayment()
        runCurrent()
        assertTrue(online.requests.isEmpty())
        assertTrue(offline.requests.isEmpty())
        assertEquals(1, cart.getCart().value.items.size)
        assertEquals("Jumlah pembayaran kurang dari total belanja", vm.state.value.errorMessage)

        vm.selectQuickTender(100_000L)
        assertEquals(50_000L, vm.state.value.payment.totalAmount)
        assertEquals(50_000L, vm.state.value.payment.changeAmount)
        vm.confirmPayment()
        runCurrent()

        val request = online.requests.single()
        assertEquals("sale-id", request.clientTransactionId)
        assertEquals("CASH", request.payment.method)
        assertEquals(100_000L, request.payment.amountReceived)
        assertNotEquals("client-computed change must not be sent as tender", vm.state.value.payment.changeAmount, request.payment.amountReceived)
    }

    @Test fun qrisCanSubmitWithoutTenderAndNeverLeaksPreviousCashTender() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()

        vm.selectQuickTender(100_000L)
        vm.selectPaymentMethod(CheckoutPaymentMethod.QRIS)
        assertNull(vm.state.value.payment.amountReceived)
        assertTrue(vm.state.value.payment.canSubmit)
        vm.confirmPayment()
        runCurrent()

        val request = online.requests.single()
        assertEquals("sale-id", request.clientTransactionId)
        assertEquals("QRIS", request.payment.method)
        assertNull(request.payment.amountReceived)
    }

    @Test fun qrisNetworkFailureQueuesV2SnapshotWithTotalAndSameRequest() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        vm.selectPaymentMethod(CheckoutPaymentMethod.QRIS)
        vm.confirmPayment()
        runCurrent()
        online.complete(Result.failure(IOException("offline")))
        advanceUntilIdle()

        assertEquals(online.requests.single(), offline.requests.single())
        assertEquals("sale-id", offline.requests.single().clientTransactionId)
        assertEquals("active-shift", offline.requests.single().cashierSessionId)
        assertEquals("QRIS", offline.requests.single().payment.method)
        assertNull(offline.requests.single().payment.amountReceived)
        assertEquals(50_000L, offline.snapshots.single().paymentAmount)
        assertEquals(listOf("ice", "shot"), offline.requests.single().items.single().modifierOptionIds)
        assertEquals("Sedikit es", offline.requests.single().items.single().note)
        assertTrue(cart.getCart().value.items.isEmpty())
    }

    @Test fun cashNetworkFailureQueuesV2SnapshotWithTenderAndSameRequest() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        vm.selectQuickTender(100_000L)
        vm.confirmPayment()
        runCurrent()
        online.complete(Result.failure(IOException("offline")))
        advanceUntilIdle()

        assertEquals(online.requests.single(), offline.requests.single())
        assertEquals("sale-id", offline.requests.single().clientTransactionId)
        assertEquals("active-shift", offline.requests.single().cashierSessionId)
        assertEquals("CASH", offline.requests.single().payment.method)
        assertEquals(100_000L, offline.requests.single().payment.amountReceived)
        assertEquals(100_000L, offline.snapshots.single().paymentAmount)
        assertTrue(cart.getCart().value.items.isEmpty())
    }

    @Test fun duplicateConfirmWhileSubmittingIsGuarded() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()

        vm.selectExactCash()
        vm.confirmPayment()
        vm.confirmPayment()
        runCurrent()

        assertEquals(1, online.requests.size)
        assertTrue(offline.requests.isEmpty())
    }

    @Test fun switchingQrisBackToCashRequiresFreshTenderAndDoesNotRegenerateIdentity() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        val idBefore = handle.get<String>(CheckoutTransactionIdentity.KEY)

        vm.selectQuickTender(100_000L)
        vm.enterManualCash("60000")
        vm.selectPaymentMethod(CheckoutPaymentMethod.QRIS)
        vm.selectPaymentMethod(CheckoutPaymentMethod.CASH)

        assertEquals(idBefore, handle.get<String>(CheckoutTransactionIdentity.KEY))
        assertNull(vm.state.value.payment.amountReceived)
        assertFalse(vm.state.value.payment.canSubmit)
        vm.confirmPayment()
        runCurrent()
        assertTrue(online.requests.isEmpty())
        assertTrue(offline.requests.isEmpty())
    }

    @Test fun tenderAndMethodChangesDoNotChangeClientTransactionId() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        val idBefore = handle.get<String>(CheckoutTransactionIdentity.KEY)

        vm.selectExactCash()
        vm.selectQuickTender(100_000L)
        vm.enterManualCash("60000")
        vm.selectPaymentMethod(CheckoutPaymentMethod.QRIS)
        vm.selectPaymentMethod(CheckoutPaymentMethod.CASH)
        vm.selectExactCash()
        vm.confirmPayment()
        runCurrent()

        assertEquals(idBefore, handle.get<String>(CheckoutTransactionIdentity.KEY))
        assertEquals("sale-id", online.requests.single().clientTransactionId)
    }

    @Test fun offlineFallbackPreservesDuplicateConfiguredCartLines() = runTest(dispatcher) {
        val vm = viewModelWithDuplicateConfiguredLines()
        runCurrent()
        assertEquals(2, cart.getCart().value.items.size)
        vm.selectPaymentMethod(CheckoutPaymentMethod.QRIS)
        vm.confirmPayment()
        runCurrent()
        online.complete(Result.failure(IOException("offline")))
        advanceUntilIdle()

        val queued = offline.requests.single()
        assertEquals("sale-id", queued.clientTransactionId)
        assertEquals("active-shift", queued.cashierSessionId)
        assertEquals(2, queued.items.size)
        assertEquals(listOf("ice"), queued.items[0].modifierOptionIds)
        assertEquals("Sedikit es", queued.items[0].note)
        assertEquals(listOf("hot"), queued.items[1].modifierOptionIds)
        assertEquals("Tanpa gula", queued.items[1].note)
        assertTrue(cart.getCart().value.items.isEmpty())
    }

    @Test fun stableIdAcrossAllPaymentActions() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        val idBefore = handle.get<String>(CheckoutTransactionIdentity.KEY)

        vm.selectExactCash()
        assertEquals(idBefore, handle.get<String>(CheckoutTransactionIdentity.KEY))

        vm.selectQuickTender(100_000L)
        assertEquals(idBefore, handle.get<String>(CheckoutTransactionIdentity.KEY))

        vm.enterManualCash("60000")
        assertEquals(idBefore, handle.get<String>(CheckoutTransactionIdentity.KEY))

        vm.selectPaymentMethod(CheckoutPaymentMethod.QRIS)
        assertEquals(idBefore, handle.get<String>(CheckoutTransactionIdentity.KEY))

        vm.selectPaymentMethod(CheckoutPaymentMethod.CASH)
        assertEquals(idBefore, handle.get<String>(CheckoutTransactionIdentity.KEY))

        vm.selectExactCash()
        assertEquals(idBefore, handle.get<String>(CheckoutTransactionIdentity.KEY))
    }

    /**
     * M16G-S1: an attached cart routes to the Held Order endpoint, so the normal
     * transaction path, the offline queue and hardware stay untouched.
     */
    @Test fun heldOrderAttachmentRoutesToHeldOrderCheckoutBeforeAnyNormalSideEffect() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        cart.attachHeldOrderIdentity("held-1", 3, "Meja 3")
        heldOrders.checkoutResult = Result.success(
            HeldOrderCheckoutResult(transaction(), replayed = false),
        )

        vm.selectExactCash()
        vm.confirmPayment()
        advanceUntilIdle()

        assertEquals(1, heldOrders.checkoutCalls.size)
        assertEquals("held-1", heldOrders.checkoutCalls.single().id)
        assertEquals(3, heldOrders.checkoutCalls.single().expectedVersion)
        assertTrue(online.requests.isEmpty())
        assertTrue(offline.requests.isEmpty())
        assertEquals(1, printAfterCheckout.invocations.size)
        assertTrue(printAfterCheckout.invocations.single().isOriginalOnlineCheckout)
        assertNull(cart.getHeldOrderIdentity().value)
    }

    @Test fun heldOrderDetachmentRestoresNormalCheckout() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        cart.attachHeldOrderIdentity("held-1", 3, "Meja 3")

        // Detaching without checking out must hand the cart back to the normal
        // online path; the held-order endpoint is never reached.
        cart.detachHeldOrderIdentity()
        assertFalse(cart.isAttachedToHeldOrder())

        vm.selectExactCash()
        vm.confirmPayment()
        runCurrent()
        online.complete(Result.success(transaction()))
        advanceUntilIdle()

        assertEquals(1, online.requests.size)
        assertEquals(1, printAfterCheckout.invocations.size)
        assertTrue(heldOrders.checkoutCalls.isEmpty())
    }

    @Test fun offlineQueueSuccessClearsCartOnlyAfterQueuePersistenceCompletes() = runTest(dispatcher) {
        offline.suspendUntilCompleted = true
        val vm = viewModel()
        runCurrent()
        vm.selectExactCash()
        vm.confirmPayment()
        runCurrent()

        online.complete(Result.failure(IOException("offline")))
        runCurrent()

        assertEquals(1, offline.requests.size)
        assertTrue(vm.state.value.isLoading)
        assertEquals(1, cart.getCart().value.items.size)
        assertNull(vm.state.value.offlineQueuedClientTransactionId)

        offline.complete(Result.success(offlineTransaction()))
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertEquals("sale-id", vm.state.value.offlineQueuedClientTransactionId)
        assertTrue(cart.getCart().value.items.isEmpty())
    }

    private fun transaction() = Transaction(
        id = "server-id", clientTransactionId = "sale-id", outletId = outlet.id,
        userId = "cashier", customerId = null, status = "COMPLETED",
        subtotal = 50_000, discount = 0, tax = 0, total = 50_000,
        items = emptyList(), payments = emptyList(), change = 10_000, createdAt = "now",
    )

    private fun offlineTransaction() = OfflineTransaction(
        "row", "tenant", "cashier", "sale-id", "selected-outlet", "{}", "PENDING",
        null, null, 0, 0, 0,
    )

    private class FakeTransactions : TransactionRepository {
        val requests = mutableListOf<V1TransactionRequest>()
        var clearObserved = 0
        private var pending: kotlinx.coroutines.CompletableDeferred<Result<Transaction>>? = null
        override suspend fun createV1Transaction(request: V1TransactionRequest): Result<Transaction> {
            requests += request
            pending = kotlinx.coroutines.CompletableDeferred()
            return pending!!.await()
        }
        fun complete(result: Result<Transaction>) { pending!!.complete(result) }
        override suspend fun getTransactions(
            from: String?,
            to: String?,
        ): Result<List<Transaction>> = error("Unused")
        override suspend fun getTransactionDetail(transactionId: String): Result<Transaction> = error("Unused")
        override suspend fun createTransaction(clientTransactionId: String, outletId: String,
            customerId: String?, items: List<CartItem>, paymentAmount: Long): Result<Transaction> = error("Legacy path used")
    }

    private class FakeQueue : OfflineSyncRepository {
        val requests = mutableListOf<V1TransactionRequest>()
        val snapshots = mutableListOf<OfflineFinancialSnapshot>()
        var result: Result<OfflineTransaction> = Result.success(OfflineTransaction(
            "row", "tenant", "cashier", "sale-id", "selected-outlet", "{}", "PENDING",
            null, null, 0, 0, 0))
        var suspendUntilCompleted = false
        private var pending: kotlinx.coroutines.CompletableDeferred<Result<OfflineTransaction>>? = null
        override suspend fun queueV1Transaction(request: V1TransactionRequest,
            financialSnapshot: OfflineFinancialSnapshot): Result<OfflineTransaction> {
            requests += request
            snapshots += financialSnapshot
            if (suspendUntilCompleted) {
                pending = kotlinx.coroutines.CompletableDeferred()
                return pending!!.await()
            }
            return result
        }
        fun complete(result: Result<OfflineTransaction>) { pending!!.complete(result) }
        override suspend fun queueTransaction(clientTransactionId: String, outletId: String,
            customerId: String?, items: List<CartItem>, paymentAmount: Long,
            financialSnapshot: OfflineFinancialSnapshot): Result<OfflineTransaction> = error("Legacy queue used")
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

    /**
     * M16G-S1: the normal (non-held-order) checkout path must never reach the
     * Held Order endpoint. `checkoutCalls` stays empty for those tests.
     */
    private class ConfigurableHeldOrders : HeldOrderRepository {
        val checkoutCalls = mutableListOf<HeldOrderCheckoutRequest>()

        /** `null` means "this test must never reach the Held Order endpoint". */
        var checkoutResult: Result<HeldOrderCheckoutResult>? = null

        override suspend fun checkout(request: HeldOrderCheckoutRequest): Result<HeldOrderCheckoutResult> {
            checkoutCalls += request
            return checkoutResult ?: error("checkout must not be called in this test")
        }

        override suspend fun create(request: HeldOrderCreateRequest): Result<HeldOrder> = error("Unused")
        override suspend fun list(outletId: String?, status: String, page: Int, limit: Int): Result<List<HeldOrder>> = error("Unused")
        override suspend fun get(id: String): Result<HeldOrder> = error("Unused")
        override suspend fun update(request: HeldOrderUpdateRequest): Result<HeldOrder> = error("Unused")
        override suspend fun cancel(id: String, expectedVersion: Int): Result<HeldOrder> = error("Unused")
    }

    private class FakeOutlet(outlet: Outlet) : OutletRepository {
        override val selectedOutlet: StateFlow<Outlet?> = MutableStateFlow(outlet)
        override suspend fun getOutlets(): Result<List<Outlet>> = error("Unused")
        override suspend fun selectOutlet(outlet: Outlet) = error("Unused")
        override suspend fun clearSelectedOutlet(tenantId: String?, userId: String?) = error("Unused")
        override suspend fun restoreSelectedOutlet() = error("Unused")
    }

    private class FakeShift(shift: Shift) : ShiftRepository {
        override val currentShift: StateFlow<Shift?> = MutableStateFlow(shift)
        override suspend fun getCurrentShift(): Result<Shift?> = error("Unused")
        override suspend fun openShift(outletId: String): Result<Shift> = error("Unused")
        override suspend fun closeShift(shiftId: String): Result<Shift> = error("Unused")
        override suspend fun getShiftSummary(shiftId: String): Result<ShiftSummary> = error("Unused")
        override suspend fun clearCurrentShift(tenantId: String?, userId: String?) = error("Unused")
        override suspend fun restoreCurrentShift(expectedOutletId: String?) = error("Unused")
    }

    private class FakeReceiptRepository : ReceiptRepository {
        val requestedIds = mutableListOf<String>()
        override suspend fun getReceipt(transactionId: String): Result<Receipt> {
            requestedIds += transactionId
            return Result.success(Receipt(
                transactionId = transactionId,
                clientTransactionId = "sale-id",
                status = "COMPLETED",
                createdAt = "now",
                tenant = ReceiptTenant("tenant", "Tenant", null),
                outlet = ReceiptOutlet("selected-outlet", "Outlet", null),
                cashier = ReceiptCashier("cashier", "Cashier"),
                customer = null,
                items = emptyList(),
                payment = Payment(
                    id = "payment-id",
                    method = "CASH",
                    status = "COMPLETED",
                    amount = 60_000,
                    paidAt = "now",
                    amountReceived = 60_000,
                    changeAmount = 10_000,
                ),
                subtotal = 50_000,
                discount = 0,
                tax = 0,
                total = 50_000,
                change = 10_000,
            ))
        }
    }

    private class FakePrintAfterCheckoutUseCase : PrintAfterCheckoutUseCase() {
        data class Invocation(
            val receipt: Receipt,
            val paymentMethod: String,
            val isOriginalOnlineCheckout: Boolean,
        )

        val invocations = mutableListOf<Invocation>()
        var result: Result = Result.Success(printed = true, drawerOpened = true)

        override suspend fun invoke(
            receipt: Receipt,
            paymentMethod: String,
            isOriginalOnlineCheckout: Boolean,
        ): Result {
            invocations += Invocation(receipt, paymentMethod, isOriginalOnlineCheckout)
            return result
        }
    }
}
