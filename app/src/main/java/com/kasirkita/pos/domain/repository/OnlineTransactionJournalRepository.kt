package com.kasirkita.pos.domain.repository

import com.kasirkita.pos.domain.model.OnlineTransactionJournal
import com.kasirkita.pos.domain.model.OnlineTransactionJournalDraft
import kotlinx.coroutines.flow.Flow

interface OnlineTransactionJournalRepository {
    suspend fun prepare(draft: OnlineTransactionJournalDraft): Result<OnlineTransactionJournal>

    suspend fun getUnresolved(
        tenantId: String,
        userId: String,
        outletId: String,
    ): Result<OnlineTransactionJournal?>

    suspend fun getUnresolvedForAccount(
        tenantId: String,
        userId: String,
    ): Result<OnlineTransactionJournal?>

    suspend fun recoverSubmittingForAccount(
        tenantId: String,
        userId: String,
    ): Result<Int>

    fun observeUnresolvedCount(tenantId: String, userId: String): Flow<Int>

    suspend fun markSubmitting(journal: OnlineTransactionJournal): Result<Boolean>

    suspend fun markUncertain(
        journal: OnlineTransactionJournal,
        error: String,
    ): Result<Boolean>

    suspend fun markRequiresReconciliation(
        journal: OnlineTransactionJournal,
        error: String,
    ): Result<Boolean>

    suspend fun markConfirmed(
        journal: OnlineTransactionJournal,
        serverTransactionId: String,
    ): Result<Boolean>

    suspend fun markRejected(
        journal: OnlineTransactionJournal,
        error: String,
    ): Result<Boolean>
}
