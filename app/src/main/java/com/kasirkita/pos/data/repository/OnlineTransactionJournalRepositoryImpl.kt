package com.kasirkita.pos.data.repository

import com.kasirkita.pos.core.database.dao.OnlineTransactionJournalDao
import com.kasirkita.pos.core.database.entity.OnlineTransactionJournalEntity
import com.kasirkita.pos.domain.model.OnlineTransactionJournal
import com.kasirkita.pos.domain.model.OnlineTransactionJournalDraft
import com.kasirkita.pos.domain.model.OnlineTransactionJournalState
import com.kasirkita.pos.domain.repository.OnlineTransactionJournalRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class OnlineTransactionJournalRepositoryImpl @Inject constructor(
    private val dao: OnlineTransactionJournalDao,
) : OnlineTransactionJournalRepository {

    override suspend fun prepare(
        draft: OnlineTransactionJournalDraft,
    ): Result<OnlineTransactionJournal> = runCatching {
        require(draft.tenantId.isNotBlank())
        require(draft.userId.isNotBlank())
        require(draft.outletId.isNotBlank())
        require(draft.clientTransactionId.isNotBlank())
        require(draft.requestJson.isNotBlank())

        val entity = OnlineTransactionJournalEntity(
            tenantId = draft.tenantId,
            userId = draft.userId,
            outletId = draft.outletId,
            clientTransactionId = draft.clientTransactionId,
            requestType = draft.requestType,
            requestJson = draft.requestJson,
            paymentMethod = draft.paymentMethod,
            state = OnlineTransactionJournalState.PREPARED.name,
            serverTransactionId = null,
            lastError = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        val persisted = dao.insertIfAbsent(entity)
        require(
            persisted.requestType == entity.requestType &&
                persisted.requestJson == entity.requestJson &&
                persisted.paymentMethod == entity.paymentMethod,
        ) {
            "Online journal contains a different request for the same client transaction ID"
        }
        persisted.toDomain()
    }

    override suspend fun getUnresolved(
        tenantId: String,
        userId: String,
        outletId: String,
    ): Result<OnlineTransactionJournal?> = runCatching {
        dao.getUnresolved(tenantId, userId, outletId)?.toDomain()
    }

    override suspend fun getUnresolvedForAccount(
        tenantId: String,
        userId: String,
    ): Result<OnlineTransactionJournal?> = runCatching {
        dao.getUnresolvedForAccount(tenantId, userId)?.toDomain()
    }

    override suspend fun recoverSubmittingForAccount(
        tenantId: String,
        userId: String,
    ): Result<Int> = runCatching {
        dao.recoverSubmittingForAccount(
            tenantId = tenantId,
            userId = userId,
            error = "Application restarted while transaction submission was in flight",
            updatedAt = System.currentTimeMillis(),
        )
    }

    override fun observeUnresolvedCount(tenantId: String, userId: String): Flow<Int> =
        dao.observeUnresolvedCount(tenantId, userId)

    override suspend fun markSubmitting(journal: OnlineTransactionJournal): Result<Boolean> =
        runCatching {
            dao.markSubmitting(
                journal.tenantId,
                journal.userId,
                journal.outletId,
                journal.clientTransactionId,
                System.currentTimeMillis(),
            ) == 1
        }

    override suspend fun markUncertain(
        journal: OnlineTransactionJournal,
        error: String,
    ): Result<Boolean> = runCatching {
        dao.markUncertain(
            journal.tenantId,
            journal.userId,
            journal.outletId,
            journal.clientTransactionId,
            error,
            System.currentTimeMillis(),
        ) == 1
    }

    override suspend fun markRequiresReconciliation(
        journal: OnlineTransactionJournal,
        error: String,
    ): Result<Boolean> = runCatching {
        dao.markRequiresReconciliation(
            journal.tenantId,
            journal.userId,
            journal.outletId,
            journal.clientTransactionId,
            error,
            System.currentTimeMillis(),
        ) == 1
    }

    override suspend fun markConfirmed(
        journal: OnlineTransactionJournal,
        serverTransactionId: String,
    ): Result<Boolean> = runCatching {
        require(serverTransactionId.isNotBlank())
        dao.markConfirmed(
            journal.tenantId,
            journal.userId,
            journal.outletId,
            journal.clientTransactionId,
            serverTransactionId,
            System.currentTimeMillis(),
        ) == 1
    }

    override suspend fun markRejected(
        journal: OnlineTransactionJournal,
        error: String,
    ): Result<Boolean> = runCatching {
        dao.markRejected(
            journal.tenantId,
            journal.userId,
            journal.outletId,
            journal.clientTransactionId,
            error,
            System.currentTimeMillis(),
        ) == 1
    }

    private fun OnlineTransactionJournalEntity.toDomain() = OnlineTransactionJournal(
        tenantId = tenantId,
        userId = userId,
        outletId = outletId,
        clientTransactionId = clientTransactionId,
        requestType = requestType,
        requestJson = requestJson,
        paymentMethod = paymentMethod,
        state = OnlineTransactionJournalState.valueOf(state),
        serverTransactionId = serverTransactionId,
        lastError = lastError,
    )
}
