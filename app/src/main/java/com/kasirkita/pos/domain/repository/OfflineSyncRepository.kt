package com.kasirkita.pos.domain.repository

import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.OfflineFinancialSnapshot
import com.kasirkita.pos.domain.model.OfflineQueueSummary
import com.kasirkita.pos.domain.model.OfflineTransaction
import com.kasirkita.pos.domain.model.SyncOutcome
import kotlinx.coroutines.flow.Flow

interface OfflineSyncRepository {
    suspend fun queueTransaction(
        clientTransactionId: String,
        outletId: String,
        customerId: String?,
        items: List<CartItem>,
        paymentAmount: Long,
        financialSnapshot: OfflineFinancialSnapshot,
    ): Result<OfflineTransaction>

    suspend fun getPendingTransactions(limit: Int = 100): Result<List<OfflineTransaction>>

    suspend fun getTransaction(clientTransactionId: String): Result<OfflineTransaction?>

    suspend fun getFailedTransactions(limit: Int = 100): Result<List<OfflineTransaction>>

    suspend fun getActionRequiredTransactions(limit: Int = 100): Result<List<OfflineTransaction>>

    fun observeQueueSummary(): Flow<OfflineQueueSummary>

    fun observePendingCount(): Flow<Int>

    suspend fun syncPendingTransactions(): Result<SyncOutcome>

    suspend fun syncPendingTransactionsForAccount(
        tenantId: String,
        userId: String,
    ): Result<SyncOutcome>

    suspend fun retryFailedTransaction(clientTransactionId: String): Result<SyncOutcome>

    suspend fun retryFailedTransactions(): Result<SyncOutcome>
}
