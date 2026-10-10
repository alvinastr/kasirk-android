package com.kasirkita.pos.presentation.home

import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.DailySalesReport
import com.kasirkita.pos.domain.model.OfflineFinancialSnapshot
import com.kasirkita.pos.domain.model.OfflineQueueSummary
import com.kasirkita.pos.domain.model.OfflineTransaction
import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.domain.model.ShiftSummary
import com.kasirkita.pos.domain.model.ShiftSummaryParty
import com.kasirkita.pos.domain.model.ShiftSummaryTotals
import com.kasirkita.pos.domain.model.SyncOutcome
import com.kasirkita.pos.domain.model.UserRole
import com.kasirkita.pos.domain.model.V1TransactionRequest
import com.kasirkita.pos.domain.repository.OfflineSyncRepository
import com.kasirkita.pos.domain.repository.ReportRepository
import com.kasirkita.pos.domain.repository.ShiftRepository
import com.kasirkita.pos.domain.usecase.GetDailySalesUseCase
import com.kasirkita.pos.domain.usecase.SyncPendingTransactionsUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var report: FakeReportRepository
    private lateinit var shift: FakeShiftRepository
    private lateinit var vm: HomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        report = FakeReportRepository()
        shift = FakeShiftRepository()
        vm = HomeViewModel(
            getDailySales = GetDailySalesUseCase(report),
            shiftRepository = shift,
            offlineSyncRepository = FakeOfflineSyncRepository(),
            syncPendingTransactions = SyncPendingTransactionsUseCase(FakeOfflineSyncRepository()),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun ownerAndAdmin_callDailySales_cashierAndNullDoNot() = runTest {
        vm.refresh(UserRole.OWNER, "out-1", null, tenantId = "tenant", userId = "user", force = true)
        advanceUntilIdle()
        vm.refresh(UserRole.ADMIN, "out-1", null, tenantId = "tenant", userId = "user", force = true)
        advanceUntilIdle()
        vm.refresh(UserRole.CASHIER, "out-1", null, tenantId = "tenant", userId = "user", force = true)
        vm.refresh(null, "out-1", null, tenantId = "tenant", userId = "user", force = true)
        advanceUntilIdle()

        assertEquals(2, report.calls)
    }

    @Test
    fun dailySuccessZeroIsAvailableZero() = runTest {
        report.result = Result.success(daily(total = 0, outletId = "out-1"))

        vm.refresh(UserRole.OWNER, "out-1", null, force = true)
        advanceUntilIdle()

        assertEquals(DashboardMetric.Success(daily(total = 0, outletId = "out-1")), vm.dashboardState.value.dailySales)
    }

    @Test
    fun dailyErrorIsNotZeroAndRetryCanRecover() = runTest {
        report.result = Result.failure(IOException("offline"))
        vm.refresh(UserRole.OWNER, "out-1", null, force = true)
        advanceUntilIdle()
        assertTrue(vm.dashboardState.value.dailySales is DashboardMetric.Error)

        report.result = Result.success(daily(total = 42, outletId = "out-1"))
        vm.retryDaily(UserRole.OWNER, "out-1")
        advanceUntilIdle()
        assertEquals(42L, (vm.dashboardState.value.dailySales as DashboardMetric.Success).value.totalSales)
    }

    @Test
    fun dailyOutletMismatchIsRejected() = runTest {
        report.result = Result.success(daily(total = 99, outletId = "other-outlet"))
        vm.refresh(UserRole.OWNER, "out-1", null, force = true)
        advanceUntilIdle()

        assertTrue(vm.dashboardState.value.dailySales is DashboardMetric.Error)
    }

    @Test
    fun shiftRequiresActiveContextAndValidatesIdentity() = runTest {
        vm.refresh(UserRole.CASHIER, "out-1", null, force = true)
        advanceUntilIdle()
        assertEquals(0, shift.calls)

        shift.result = Result.success(summary(shiftId = "other-shift", outletId = "out-1"))
        vm.refresh(UserRole.CASHIER, "out-1", "shift-1", force = true)
        advanceUntilIdle()
        assertTrue(vm.dashboardState.value.shiftSummary is DashboardMetric.Error)
    }

    @Test
    fun dailyRetryDoesNotStrandShiftRequest() = runTest {
        report.deferred = CompletableDeferred()
        shift.deferred = CompletableDeferred()
        vm.refresh(UserRole.OWNER, "out-1", "shift-1", force = true)
        advanceUntilIdle()
        report.deferred!!.complete(Result.success(daily(10, "out-1")))
        advanceUntilIdle()
        vm.retryDaily(UserRole.OWNER, "out-1")
        report.deferred = CompletableDeferred()
        shift.deferred!!.complete(Result.success(summary("shift-1", "out-1")))
        advanceUntilIdle()

        assertTrue(vm.dashboardState.value.shiftSummary is DashboardMetric.Success)
    }

    @Test
    fun staleDailyResponseAfterContextSwitchIsIgnored() = runTest {
        val old = CompletableDeferred<Result<DailySalesReport>>()
        report.deferred = old
        vm.refresh(UserRole.OWNER, "out-1", null, force = true)
        advanceUntilIdle()
        vm.refresh(UserRole.OWNER, "out-2", null, force = true)
        old.complete(Result.success(daily(77, "out-1")))
        advanceUntilIdle()

        val state = vm.dashboardState.value.dailySales
        assertTrue(state !is DashboardMetric.Success || state.value.outletId == "out-2")
    }

    @Test
    fun dailyCancellationAfterContextSwitch_isIgnored() = runTest {
        val old = CompletableDeferred<Result<DailySalesReport>>()
        report.deferredByCall[1] = old
        report.deferredByCall[2] = CompletableDeferred()
        vm.refresh(UserRole.OWNER, "out-1", null, force = true)
        advanceUntilIdle()
        vm.refresh(UserRole.OWNER, "out-2", null, force = true)
        old.complete(Result.failure(CancellationException("cancelled")))
        advanceUntilIdle()

        assertTrue(vm.dashboardState.value.contextKey?.outletId == "out-2")
        assertTrue(vm.dashboardState.value.dailySales is DashboardMetric.Loading)
    }

    @Test
    fun shiftCancellationAfterContextSwitch_isIgnored() = runTest {
        val old = CompletableDeferred<Result<ShiftSummary>>()
        shift.deferredByCall[1] = old
        shift.deferredByCall[2] = CompletableDeferred()
        vm.refresh(UserRole.CASHIER, "out-1", "shift-1", force = true)
        advanceUntilIdle()
        vm.refresh(UserRole.CASHIER, "out-2", "shift-2", force = true)
        old.complete(Result.failure(CancellationException("cancelled")))
        advanceUntilIdle()

        assertTrue(vm.dashboardState.value.contextKey?.shiftId == "shift-2")
        assertTrue(vm.dashboardState.value.shiftSummary is DashboardMetric.Loading)
    }

    @Test
    fun shiftRetryDoesNotStrandDailyRequest() = runTest {
        report.deferred = CompletableDeferred()
        shift.result = Result.failure(IOException("offline"))
        vm.refresh(UserRole.OWNER, "out-1", "shift-1", force = true)
        advanceUntilIdle()
        vm.retryShift("out-1", "shift-1")
        report.deferred!!.complete(Result.success(daily(12, "out-1")))
        advanceUntilIdle()

        assertTrue(vm.dashboardState.value.dailySales is DashboardMetric.Success)
    }

    @Test
    fun repeatedSameContextRefreshes_coalesceInFlightGroups() = runTest {
        report.deferred = CompletableDeferred()
        shift.deferred = CompletableDeferred()
        vm.refresh(UserRole.OWNER, "out-1", "shift-1", force = false)
        advanceUntilIdle()
        vm.refresh(UserRole.OWNER, "out-1", "shift-1", force = false)
        advanceUntilIdle()

        assertEquals(1, report.calls)
        assertEquals(1, shift.calls)
        report.deferred!!.complete(Result.success(daily(9, "out-1")))
        shift.deferred!!.complete(Result.success(summary("shift-1", "out-1")))
        advanceUntilIdle()
        assertTrue(vm.dashboardState.value.dailySales is DashboardMetric.Success)
        assertTrue(vm.dashboardState.value.shiftSummary is DashboardMetric.Success)
    }

    private fun daily(total: Long, outletId: String?) = DailySalesReport("2026-10-10", outletId, 1, total, 0, total, 0, total, 0, total)

    private fun summary(shiftId: String, outletId: String) = ShiftSummary(
        shiftId = shiftId,
        status = "OPEN",
        outlet = ShiftSummaryParty(outletId, "Outlet"),
        cashier = ShiftSummaryParty("user", "Kasir"),
        openedAt = "2026-10-10T00:00:00Z",
        closedAt = null,
        generatedAt = "2026-10-10T00:00:00Z",
        transactionCount = 2,
        totals = ShiftSummaryTotals(100, 60, 30, 10),
        products = emptyList(),
    )
}

private class FakeReportRepository : ReportRepository {
    var calls = 0
    var result: Result<DailySalesReport> = Result.success(DailySalesReport("2026-10-10", "out-1", 0, 0, 0, 0, 0, 0, 0, 0))
    var deferred: CompletableDeferred<Result<DailySalesReport>>? = null
    val deferredByCall = mutableMapOf<Int, CompletableDeferred<Result<DailySalesReport>>>()
    override suspend fun getDailySales(date: String, outletId: String?): Result<DailySalesReport> {
        calls++
        return deferredByCall[calls]?.await() ?: deferred?.await() ?: result
    }
    override suspend fun getSalesSummary(startDate: String, endDate: String, outletId: String?) = error("unused")
    override suspend fun getTopProducts(startDate: String, endDate: String, limit: Int, outletId: String?) = error("unused")
}

private class FakeShiftRepository : ShiftRepository {
    private val current = MutableStateFlow<com.kasirkita.pos.domain.model.Shift?>(null)
    override val currentShift: StateFlow<com.kasirkita.pos.domain.model.Shift?> = current.asStateFlow()
    var calls = 0
    var result: Result<ShiftSummary> = Result.failure(IOException("not configured"))
    var deferred: CompletableDeferred<Result<ShiftSummary>>? = null
    val deferredByCall = mutableMapOf<Int, CompletableDeferred<Result<ShiftSummary>>>()
    override suspend fun getShiftSummary(shiftId: String): Result<ShiftSummary> {
        calls++
        return deferredByCall[calls]?.await() ?: deferred?.await() ?: result
    }
    override suspend fun getCurrentShift() = Result.success(current.value)
    override suspend fun openShift(outletId: String) = error("unused")
    override suspend fun closeShift(shiftId: String) = error("unused")
    override suspend fun clearCurrentShift(tenantId: String?, userId: String?) { current.value = null }
    override suspend fun restoreCurrentShift(expectedOutletId: String?) = Unit
}

private class FakeOfflineSyncRepository : OfflineSyncRepository {
    override fun observeQueueSummary() = flowOf(OfflineQueueSummary(0, 0))
    override fun observePendingCount() = flowOf(0)
    override suspend fun syncPendingTransactions() = Result.success(SyncOutcome.AuthenticationUnavailable)
    override suspend fun queueTransaction(clientTransactionId: String, outletId: String, customerId: String?, items: List<CartItem>, paymentAmount: Long, financialSnapshot: OfflineFinancialSnapshot) = error("unused")
    override suspend fun queueV1Transaction(request: V1TransactionRequest, financialSnapshot: OfflineFinancialSnapshot) = error("unused")
    override suspend fun getPendingTransactions(limit: Int) = error("unused")
    override suspend fun getTransaction(clientTransactionId: String) = error("unused")
    override suspend fun getFailedTransactions(limit: Int) = error("unused")
    override suspend fun getActionRequiredTransactions(limit: Int) = error("unused")
    override suspend fun getRecoveryTransactions(limit: Int) = error("unused")
    override suspend fun syncPendingTransactionsForAccount(tenantId: String, userId: String) = error("unused")
    override suspend fun retryFailedTransaction(clientTransactionId: String) = error("unused")
    override suspend fun deleteFailedTransaction(clientTransactionId: String) = error("unused")
    override suspend fun acknowledgeReconciliation(clientTransactionId: String) = error("unused")
    override suspend fun retryFailedTransactions() = error("unused")
}
