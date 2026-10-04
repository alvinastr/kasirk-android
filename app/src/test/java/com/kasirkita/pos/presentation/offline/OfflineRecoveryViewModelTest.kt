package com.kasirkita.pos.presentation.offline

import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.OfflineFinancialSnapshot
import com.kasirkita.pos.domain.model.OfflineQueueSummary
import com.kasirkita.pos.domain.model.OfflineTransaction
import com.kasirkita.pos.domain.model.OfflineTransactionFailureType
import com.kasirkita.pos.domain.model.OfflineTransactionStatus
import com.kasirkita.pos.domain.model.SyncOutcome
import com.kasirkita.pos.domain.model.SyncResult
import com.kasirkita.pos.domain.model.V1TransactionRequest
import com.kasirkita.pos.domain.repository.OfflineSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineRecoveryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun load_exposesRecoveryTransactions() = runTest(dispatcher) {
        val repository = FakeOfflineSyncRepository(
            mutableListOf(transaction("retryable", OfflineTransactionFailureType.RETRYABLE)),
        )
        val viewModel = OfflineRecoveryViewModel(repository)

        advanceUntilIdle()

        assertEquals(listOf("retryable"), viewModel.state.value.transactions.map { it.id })
        assertEquals(false, viewModel.state.value.isLoading)
    }

    @Test
    fun invalidActions_areIgnoredForProtectedStatuses() = runTest(dispatcher) {
        val repository = FakeOfflineSyncRepository(
            mutableListOf(
                transaction("retryable", OfflineTransactionFailureType.RETRYABLE),
                transaction(
                    "reconciliation",
                    OfflineTransactionFailureType.RECONCILIATION_REQUIRED,
                ),
            ),
        )
        val viewModel = OfflineRecoveryViewModel(repository)
        advanceUntilIdle()

        viewModel.retry("retryable")
        viewModel.delete("reconciliation")
        advanceUntilIdle()

        assertEquals(0, repository.retryCalls)
        assertEquals(0, repository.deleteCalls)
        assertEquals(2, viewModel.state.value.transactions.size)
    }

    @Test
    fun confirmedActions_removeOnlyTheirEligibleRecords() = runTest(dispatcher) {
        val repository = FakeOfflineSyncRepository(
            mutableListOf(
                transaction("rejected", OfflineTransactionFailureType.REJECTED),
                transaction(
                    "reconciliation",
                    OfflineTransactionFailureType.RECONCILIATION_REQUIRED,
                ),
            ),
        )
        val viewModel = OfflineRecoveryViewModel(repository)
        advanceUntilIdle()

        viewModel.delete("rejected")
        advanceUntilIdle()
        viewModel.acknowledge("reconciliation")
        advanceUntilIdle()

        assertEquals(1, repository.deleteCalls)
        assertEquals(1, repository.acknowledgeCalls)
        assertTrue(viewModel.state.value.transactions.isEmpty())
        assertEquals(
            "Pemeriksaan transaksi telah dikonfirmasi.",
            viewModel.state.value.noticeMessage,
        )
    }

    private fun transaction(
        id: String,
        failureType: OfflineTransactionFailureType,
    ) = OfflineTransaction(
        id = id,
        tenantId = "tenant-id",
        userId = "user-id",
        clientTransactionId = id,
        outletId = "outlet-id",
        payloadJson = "{}",
        status = when (failureType) {
            OfflineTransactionFailureType.RETRYABLE -> OfflineTransactionStatus.RETRYABLE
            OfflineTransactionFailureType.REJECTED -> OfflineTransactionStatus.FAILED
            OfflineTransactionFailureType.RECONCILIATION_REQUIRED ->
                OfflineTransactionStatus.RECONCILIATION_REQUIRED
        },
        serverTransactionId = null,
        lastError = "error",
        retryCount = 1,
        createdAt = 1L,
        updatedAt = 1L,
        failureType = failureType,
    )

    private class FakeOfflineSyncRepository(
        private val transactions: MutableList<OfflineTransaction>,
    ) : OfflineSyncRepository {
        var retryCalls = 0
        var deleteCalls = 0
        var acknowledgeCalls = 0

        override suspend fun getRecoveryTransactions(
            limit: Int,
        ): Result<List<OfflineTransaction>> = Result.success(transactions.toList())

        override suspend fun retryFailedTransaction(
            clientTransactionId: String,
        ): Result<SyncOutcome> {
            retryCalls++
            transactions.removeAll { it.clientTransactionId == clientTransactionId }
            return Result.success(SyncOutcome.Completed(SYNC_RESULT))
        }

        override suspend fun deleteFailedTransaction(
            clientTransactionId: String,
        ): Result<Unit> {
            deleteCalls++
            transactions.removeAll { it.clientTransactionId == clientTransactionId }
            return Result.success(Unit)
        }

        override suspend fun acknowledgeReconciliation(
            clientTransactionId: String,
        ): Result<Unit> {
            acknowledgeCalls++
            transactions.removeAll { it.clientTransactionId == clientTransactionId }
            return Result.success(Unit)
        }

        override fun observeQueueSummary(): Flow<OfflineQueueSummary> =
            flowOf(OfflineQueueSummary(0, 0))

        override fun observePendingCount(): Flow<Int> = flowOf(0)

        override suspend fun queueTransaction(
            clientTransactionId: String,
            outletId: String,
            customerId: String?,
            items: List<CartItem>,
            paymentAmount: Long,
            financialSnapshot: OfflineFinancialSnapshot,
        ): Result<OfflineTransaction> = error("Not used")

        override suspend fun queueV1Transaction(
            request: V1TransactionRequest,
            financialSnapshot: OfflineFinancialSnapshot,
        ): Result<OfflineTransaction> = error("Not used")

        override suspend fun getPendingTransactions(
            limit: Int,
        ): Result<List<OfflineTransaction>> = error("Not used")

        override suspend fun getTransaction(
            clientTransactionId: String,
        ): Result<OfflineTransaction?> = error("Not used")

        override suspend fun getFailedTransactions(
            limit: Int,
        ): Result<List<OfflineTransaction>> = error("Not used")

        override suspend fun getActionRequiredTransactions(
            limit: Int,
        ): Result<List<OfflineTransaction>> = error("Not used")

        override suspend fun syncPendingTransactions(): Result<SyncOutcome> = error("Not used")

        override suspend fun syncPendingTransactionsForAccount(
            tenantId: String,
            userId: String,
        ): Result<SyncOutcome> = error("Not used")

        override suspend fun retryFailedTransactions(): Result<SyncOutcome> = error("Not used")
    }

    private companion object {
        val SYNC_RESULT = SyncResult(total = 1, synced = 1, failed = 0, pending = 0)
    }
}
