package com.kasirkita.pos.domain.repository

import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.OfflineTransaction
import com.kasirkita.pos.domain.model.SyncResult
import kotlinx.coroutines.flow.Flow

interface OfflineSyncRepository {
    suspend fun queueTransaction(
        clientTransactionId: String,
        outletId: String,
        customerId: String?,
        items: List<CartItem>,
        paymentAmount: Long,
    ): Result<OfflineTransaction>

    suspend fun getPendingTransactions(limit: Int = 100): Result<List<OfflineTransaction>>

    fun observePendingCount(): Flow<Int>

    suspend fun syncPendingTransactions(): Result<SyncResult>

    suspend fun retryFailedTransactions(): Result<SyncResult>
}
