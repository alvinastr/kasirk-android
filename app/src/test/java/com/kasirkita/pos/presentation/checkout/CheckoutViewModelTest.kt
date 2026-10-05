package com.kasirkita.pos.presentation.checkout

import androidx.lifecycle.SavedStateHandle
import com.kasirkita.pos.data.repository.CartRepositoryImpl
import com.kasirkita.pos.domain.model.*
import com.kasirkita.pos.domain.repository.*
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
    private val handle = SavedStateHandle(mapOf(CheckoutTransactionIdentity.KEY to "sale-id"))
    private val outlet = Outlet("selected-outlet", "tenant", "Outlet", null, true, "now")
    private val shift = Shift("active-shift", outlet.id, "cashier", null, null, null, null, "OPEN", "now", null)

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
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
        return CheckoutViewModel(online, QueueOfflineTransactionUseCase(offline), cart,
            FakeOutlet(outlet), FakeShift(shift), handle)
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
        vm.checkout(60_000)
        runCurrent()
        online.complete(Result.failure(IOException("offline")))
        advanceUntilIdle()
        assertEquals(1, cart.getCart().value.items.size)
        assertNull(vm.state.value.offlineQueuedClientTransactionId)
        assertEquals("sale-id", offline.requests.single().clientTransactionId)
        vm.checkout(60_000)
        runCurrent()
        assertEquals(listOf("sale-id", "sale-id"), online.requests.map { it.clientTransactionId })
        online.complete(Result.success(transaction()))
        advanceUntilIdle()
        assertTrue(cart.getCart().value.items.isEmpty())
    }

    private fun transaction() = Transaction(
        id = "server-id", clientTransactionId = "sale-id", outletId = outlet.id,
        userId = "cashier", customerId = null, status = "COMPLETED",
        subtotal = 50_000, discount = 0, tax = 0, total = 50_000,
        items = emptyList(), payments = emptyList(), change = 10_000, createdAt = "now",
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
        override suspend fun getTransactions(): Result<List<Transaction>> = error("Unused")
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
        override suspend fun queueV1Transaction(request: V1TransactionRequest,
            financialSnapshot: OfflineFinancialSnapshot): Result<OfflineTransaction> {
            requests += request
            snapshots += financialSnapshot
            return result
        }
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
}
