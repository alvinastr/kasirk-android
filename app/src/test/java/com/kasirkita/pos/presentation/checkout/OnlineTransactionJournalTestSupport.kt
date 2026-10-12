package com.kasirkita.pos.presentation.checkout

import com.kasirkita.pos.domain.model.OnlineTransactionJournal
import com.kasirkita.pos.domain.model.OnlineTransactionJournalDraft
import com.kasirkita.pos.domain.model.OnlineTransactionJournalState
import com.kasirkita.pos.domain.repository.OnlineTransactionJournalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class FakeOnlineTransactionJournalRepository : OnlineTransactionJournalRepository {
    private val entries = linkedMapOf<String, OnlineTransactionJournal>()
    private val revision = MutableStateFlow(0)
    var failPrepare = false
    var failConfirm = false
    var beforePrepare: (() -> Unit)? = null

    fun clear() {
        entries.clear()
        revision.value++
    }

    fun seed(journal: OnlineTransactionJournal) {
        entries[key(journal)] = journal
        revision.value++
    }

    override suspend fun prepare(draft: OnlineTransactionJournalDraft): Result<OnlineTransactionJournal> {
        beforePrepare?.invoke()
        if (failPrepare) return Result.failure(IllegalStateException("journal unavailable"))
        val key = key(draft.tenantId, draft.userId, draft.outletId, draft.clientTransactionId)
        val existing = entries[key]
        if (existing != null) {
            if (existing.requestJson != draft.requestJson) {
                return Result.failure(IllegalStateException("journal payload conflict"))
            }
            return Result.success(existing)
        }
        return OnlineTransactionJournal(
            tenantId = draft.tenantId,
            userId = draft.userId,
            outletId = draft.outletId,
            clientTransactionId = draft.clientTransactionId,
            requestType = draft.requestType,
            requestJson = draft.requestJson,
            paymentMethod = draft.paymentMethod,
            state = OnlineTransactionJournalState.PREPARED,
            serverTransactionId = null,
            lastError = null,
        ).also {
            entries[key] = it
            revision.value++
        }.let(Result.Companion::success)
    }

    override suspend fun getUnresolved(
        tenantId: String,
        userId: String,
        outletId: String,
    ): Result<OnlineTransactionJournal?> = Result.success(
        entries.values.firstOrNull {
            it.tenantId == tenantId && it.userId == userId && it.outletId == outletId &&
                it.state.isUnresolved()
        },
    )

    override suspend fun getUnresolvedForAccount(
        tenantId: String,
        userId: String,
    ): Result<OnlineTransactionJournal?> = Result.success(
        entries.values.firstOrNull {
            it.tenantId == tenantId && it.userId == userId && it.state.isUnresolved()
        },
    )

    override suspend fun recoverSubmittingForAccount(
        tenantId: String,
        userId: String,
    ): Result<Int> {
        val candidates = entries.values.filter {
            it.tenantId == tenantId && it.userId == userId &&
                it.state == OnlineTransactionJournalState.SUBMITTING
        }
        candidates.forEach { journal ->
            update(journal) {
                copy(
                    state = OnlineTransactionJournalState.REQUIRES_RECONCILIATION,
                    lastError = "Application restarted while transaction submission was in flight",
                )
            }
        }
        return Result.success(candidates.size)
    }

    override fun observeUnresolvedCount(tenantId: String, userId: String): Flow<Int> =
        revision.map {
            entries.values.count {
                it.tenantId == tenantId && it.userId == userId && it.state.isUnresolved()
            }
        }

    override suspend fun markSubmitting(journal: OnlineTransactionJournal): Result<Boolean> =
        transition(journal) { it.state.isRetryableForSubmission() }
            .also {
                if (it.getOrDefault(false)) {
                    update(journal) { copy(state = OnlineTransactionJournalState.SUBMITTING, lastError = null) }
                }
            }

    override suspend fun markUncertain(
        journal: OnlineTransactionJournal,
        error: String,
    ): Result<Boolean> = transition(journal) { it.state == OnlineTransactionJournalState.SUBMITTING }
        .also { if (it.getOrDefault(false)) update(journal) { copy(state = OnlineTransactionJournalState.UNCERTAIN, lastError = error) } }

    override suspend fun markRequiresReconciliation(
        journal: OnlineTransactionJournal,
        error: String,
    ): Result<Boolean> = transition(journal) { it.state.isUnresolved() }
        .also { if (it.getOrDefault(false)) update(journal) { copy(state = OnlineTransactionJournalState.REQUIRES_RECONCILIATION, lastError = error) } }

    override suspend fun markConfirmed(
        journal: OnlineTransactionJournal,
        serverTransactionId: String,
    ): Result<Boolean> {
        if (failConfirm) return Result.failure(IllegalStateException("journal unavailable"))
        return transition(journal) { it.state == OnlineTransactionJournalState.SUBMITTING }
            .also { if (it.getOrDefault(false)) update(journal) { copy(state = OnlineTransactionJournalState.CONFIRMED, serverTransactionId = serverTransactionId) } }
    }

    override suspend fun markRejected(
        journal: OnlineTransactionJournal,
        error: String,
    ): Result<Boolean> = transition(journal) { it.state == OnlineTransactionJournalState.SUBMITTING }
        .also { if (it.getOrDefault(false)) update(journal) { copy(state = OnlineTransactionJournalState.REJECTED, lastError = error) } }

    fun entry(clientTransactionId: String): OnlineTransactionJournal? =
        entries.values.firstOrNull { it.clientTransactionId == clientTransactionId }

    private fun transition(
        journal: OnlineTransactionJournal,
        predicate: (OnlineTransactionJournal) -> Boolean,
    ): Result<Boolean> = Result.success(entries[key(journal)]?.let(predicate) == true)

    private fun update(
        journal: OnlineTransactionJournal,
        transform: OnlineTransactionJournal.() -> OnlineTransactionJournal,
    ) {
        val key = key(journal)
        entries[key] = entries.getValue(key).transform()
        revision.value++
    }

    private fun key(journal: OnlineTransactionJournal) =
        key(journal.tenantId, journal.userId, journal.outletId, journal.clientTransactionId)

    private fun key(tenantId: String, userId: String, outletId: String, clientTransactionId: String) =
        "$tenantId|$userId|$outletId|$clientTransactionId"

    private fun OnlineTransactionJournalState.isUnresolved() = this in setOf(
        OnlineTransactionJournalState.PREPARED,
        OnlineTransactionJournalState.SUBMITTING,
        OnlineTransactionJournalState.UNCERTAIN,
        OnlineTransactionJournalState.REQUIRES_RECONCILIATION,
    )

    private fun OnlineTransactionJournalState.isRetryableForSubmission() = this in setOf(
        OnlineTransactionJournalState.PREPARED,
        OnlineTransactionJournalState.UNCERTAIN,
        OnlineTransactionJournalState.REQUIRES_RECONCILIATION,
    )
}
