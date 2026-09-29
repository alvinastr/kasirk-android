package com.kasirkita.pos.data.repository

import com.google.gson.Gson
import com.kasirkita.pos.core.database.dao.OfflineTransactionDao
import com.kasirkita.pos.core.database.entity.OfflineTransactionEntity
import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.core.datastore.SessionIdentity
import com.kasirkita.pos.core.datastore.toSessionIdentity
import com.kasirkita.pos.data.api.SyncApi
import com.kasirkita.pos.data.model.CreateTransactionRequest
import com.kasirkita.pos.data.model.OfflineTransactionPayload
import com.kasirkita.pos.data.model.SyncTransactionError
import com.kasirkita.pos.data.model.SyncTransactionsRequest
import com.kasirkita.pos.data.model.TransactionDetailResponse
import com.kasirkita.pos.data.model.createTransactionRequest
import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.OfflineFinancialSnapshot
import com.kasirkita.pos.domain.model.OfflineQueueSummary
import com.kasirkita.pos.domain.model.OfflineTransaction
import com.kasirkita.pos.domain.model.OfflineTransactionFailureType
import com.kasirkita.pos.domain.model.OfflineTransactionSummary
import com.kasirkita.pos.domain.model.OfflineTransactionStatus
import com.kasirkita.pos.domain.model.SyncOutcome
import com.kasirkita.pos.domain.model.SyncResult
import com.kasirkita.pos.domain.model.SyncRetryableFailureReason
import com.kasirkita.pos.domain.repository.OfflineSyncRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class OfflineSyncRepositoryImpl @Inject constructor(
    private val offlineTransactionDao: OfflineTransactionDao,
    private val syncApi: SyncApi,
    private val gson: Gson,
    private val authSessionDataStore: AuthSessionDataStore,
) : OfflineSyncRepository {

    private val syncMutex = Mutex()

    override suspend fun queueTransaction(
        clientTransactionId: String,
        outletId: String,
        customerId: String?,
        items: List<CartItem>,
        paymentAmount: Long,
        financialSnapshot: OfflineFinancialSnapshot,
    ): Result<OfflineTransaction> = runCatching {
        val identity = requireIdentity()
        val request = createTransactionRequest(
            clientTransactionId = clientTransactionId,
            outletId = outletId,
            customerId = customerId,
            items = items,
            paymentAmount = paymentAmount,
        )
        require(financialSnapshot.isValidFor(request)) {
            "Offline financial snapshot does not match the checkout request"
        }
        val payload = OfflineTransactionPayload(
            version = PAYLOAD_VERSION,
            transaction = request,
            financialSnapshot = financialSnapshot,
        )
        val now = System.currentTimeMillis()
        val entity = OfflineTransactionEntity(
            id = clientTransactionId,
            tenantId = identity.tenantId,
            userId = identity.userId,
            clientTransactionId = clientTransactionId,
            outletId = outletId,
            payloadJson = gson.toJson(payload),
            status = OfflineTransactionStatus.PENDING,
            serverTransactionId = null,
            lastError = null,
            retryCount = 0,
            createdAt = now,
            updatedAt = now,
        )

        val insertResult = offlineTransactionDao.insert(entity)
        val persistedEntity = if (insertResult == INSERT_IGNORED) {
            offlineTransactionDao.getByClientTransactionId(
                tenantId = identity.tenantId,
                userId = identity.userId,
                clientTransactionId = clientTransactionId,
            )
                ?.takeIf { existing -> existing.payloadJson == entity.payloadJson }
                ?: error("Offline transaction conflict contains a different payload")
        } else {
            entity
        }

        persistedEntity.toDomain()
    }

    override suspend fun getPendingTransactions(
        limit: Int,
    ): Result<List<OfflineTransaction>> = runCatching {
        val identity = requireIdentity()
        if (limit <= 0) {
            emptyList()
        } else {
            offlineTransactionDao.getPendingTransactions(
                tenantId = identity.tenantId,
                userId = identity.userId,
                limit = limit.coerceAtMost(MAX_BATCH_SIZE),
            ).map { entity -> entity.toDomain() }
        }
    }

    override suspend fun getTransaction(
        clientTransactionId: String,
    ): Result<OfflineTransaction?> = runCatching {
        val identity = requireIdentity()
        offlineTransactionDao.getByClientTransactionId(
            tenantId = identity.tenantId,
            userId = identity.userId,
            clientTransactionId = clientTransactionId,
        )?.toDomain()
    }

    override suspend fun getFailedTransactions(
        limit: Int,
    ): Result<List<OfflineTransaction>> = runCatching {
        val identity = requireIdentity()
        if (limit <= 0) {
            emptyList()
        } else {
            offlineTransactionDao.getFailedTransactions(
                tenantId = identity.tenantId,
                userId = identity.userId,
                limit = limit.coerceAtMost(MAX_BATCH_SIZE),
            ).map { entity -> entity.toDomain() }
        }
    }

    override suspend fun getActionRequiredTransactions(
        limit: Int,
    ): Result<List<OfflineTransaction>> = runCatching {
        val identity = requireIdentity()
        if (limit <= 0) {
            emptyList()
        } else {
            offlineTransactionDao.getActionRequiredTransactions(
                tenantId = identity.tenantId,
                userId = identity.userId,
                limit = limit.coerceAtMost(MAX_BATCH_SIZE),
            ).map { entity -> entity.toDomain() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeQueueSummary(): Flow<OfflineQueueSummary> =
        authSessionDataStore.sessionFlow
            .map { session -> session?.toSessionIdentity() }
            .distinctUntilChanged()
            .flatMapLatest { identity ->
                if (identity == null) {
                    flowOf(OfflineQueueSummary(pendingCount = 0, failedCount = 0))
                } else {
                    combine(
                        offlineTransactionDao.observePendingCount(
                            tenantId = identity.tenantId,
                            userId = identity.userId,
                        ),
                        offlineTransactionDao.observeActionRequiredCount(
                            tenantId = identity.tenantId,
                            userId = identity.userId,
                        ),
                    ) { pendingCount, failedCount ->
                        OfflineQueueSummary(pendingCount, failedCount)
                    }
                }
            }

    override fun observePendingCount(): Flow<Int> = observeQueueSummary()
        .map { summary -> summary.pendingCount }
        .distinctUntilChanged()

    override suspend fun syncPendingTransactions(): Result<SyncOutcome> =
        runSynchronizedSync { identity -> syncPendingTransactions(identity) }

    override suspend fun syncPendingTransactionsForAccount(
        tenantId: String,
        userId: String,
    ): Result<SyncOutcome> = runSynchronizedSync(
        expectedIdentity = SessionIdentity(tenantId = tenantId, userId = userId),
    ) { identity ->
        syncPendingTransactions(identity)
    }

    override suspend fun retryFailedTransaction(
        clientTransactionId: String,
    ): Result<SyncOutcome> = runSynchronizedSync { identity ->
        offlineTransactionDao.retryFailedTransaction(
            tenantId = identity.tenantId,
            userId = identity.userId,
            clientTransactionId = clientTransactionId,
        )
        syncPendingTransactions(identity)
    }

    override suspend fun retryFailedTransactions(): Result<SyncOutcome> =
        runSynchronizedSync { identity ->
            offlineTransactionDao.retryFailedTransactions(
                tenantId = identity.tenantId,
                userId = identity.userId,
            )
            syncPendingTransactions(identity)
        }

    private suspend fun runSynchronizedSync(
        expectedIdentity: SessionIdentity? = null,
        operation: suspend (SessionIdentity) -> SyncOutcome,
    ): Result<SyncOutcome> = try {
        Result.success(
            syncMutex.withLock {
                val identity = currentIdentity()
                    ?: return@withLock SyncOutcome.AuthenticationUnavailable
                if (expectedIdentity != null && identity != expectedIdentity) {
                    return@withLock SyncOutcome.AuthenticationUnavailable
                }
                operation(identity)
            },
        )
    } catch (exception: CancellationException) {
        throw exception
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }

    private suspend fun syncPendingTransactions(identity: SessionIdentity): SyncOutcome {
        val pending = offlineTransactionDao.getPendingTransactions(
            tenantId = identity.tenantId,
            userId = identity.userId,
            limit = MAX_BATCH_SIZE,
        )
        if (pending.isEmpty()) {
            return SyncOutcome.Completed(syncResult(identity))
        }

        val validPayloads = mutableListOf<Pair<OfflineTransactionEntity, OfflineTransactionPayload>>()
        val actionRequired = mutableListOf<OfflineTransaction>()

        pending.forEach { entity ->
            val payload = decodePayload(entity)

            if (payload == null) {
                offlineTransactionDao.markFailed(
                    identity.tenantId,
                    identity.userId,
                    entity.clientTransactionId,
                    INVALID_LOCAL_PAYLOAD,
                )
                actionRequired += entity.asFailed(INVALID_LOCAL_PAYLOAD)
            } else {
                validPayloads += entity to payload
            }
        }

        if (validPayloads.isEmpty()) {
            return SyncOutcome.ActionRequired(
                result = syncResult(identity, pending.size, failed = actionRequired.size),
                failedTransactions = actionRequired,
            )
        }

        val clientIds = validPayloads.map { (entity) -> entity.clientTransactionId }
        val response = try {
            syncApi.syncTransactions(
                SyncTransactionsRequest(validPayloads.map { (_, payload) -> payload.transaction }),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: IOException) {
            offlineTransactionDao.markRetryable(
                identity.tenantId,
                identity.userId,
                clientIds,
                TRANSPORT_FAILURE,
            )
            return SyncOutcome.RetryableFailure(
                SyncRetryableFailureReason.TRANSPORT,
                syncResult(identity, pending.size, failed = actionRequired.size),
                TRANSPORT_FAILURE,
            )
        }

        if (response.code() == HTTP_UNAUTHORIZED) {
            return SyncOutcome.AuthenticationUnavailable
        }

        if (!response.isSuccessful) {
            if (response.code().isRetryableHttpStatus()) {
                val message = "$SERVER_FAILURE (${response.code()})"
                offlineTransactionDao.markRetryable(
                    identity.tenantId,
                    identity.userId,
                    clientIds,
                    message,
                )
                return SyncOutcome.RetryableFailure(
                    SyncRetryableFailureReason.SERVER,
                    syncResult(identity, pending.size, failed = actionRequired.size),
                    message,
                )
            }

            val message = "$BUSINESS_FAILURE (${response.code()})"
            validPayloads.forEach { (entity) ->
                offlineTransactionDao.markFailed(
                    identity.tenantId,
                    identity.userId,
                    entity.clientTransactionId,
                    message,
                )
                actionRequired += entity.asFailed(message)
            }
            return SyncOutcome.ActionRequired(
                syncResult(identity, pending.size, failed = actionRequired.size),
                actionRequired,
            )
        }

        val responseBody = response.body()
        if (responseBody == null) {
            offlineTransactionDao.markRetryable(
                identity.tenantId,
                identity.userId,
                clientIds,
                EMPTY_RESPONSE,
            )
            return SyncOutcome.RetryableFailure(
                SyncRetryableFailureReason.SERVER,
                syncResult(identity, pending.size, failed = actionRequired.size),
                EMPTY_RESPONSE,
            )
        }

        val resultsByClientId = responseBody.results.associateBy { result ->
            result.clientTransactionId
        }
        var syncedCount = 0
        var hasMissingResults = false

        validPayloads.forEach { (entity, payload) ->
            val result = resultsByClientId[entity.clientTransactionId]
            when {
                result?.status == STATUS_SYNCED && result.transaction != null -> {
                    if (payload.financialSnapshot.matches(result.transaction)) {
                        offlineTransactionDao.markSynced(
                            identity.tenantId,
                            identity.userId,
                            entity.clientTransactionId,
                            result.transaction.transactionId,
                        )
                        syncedCount++
                    } else {
                        offlineTransactionDao.markReconciliationRequired(
                            identity.tenantId,
                            identity.userId,
                            entity.clientTransactionId,
                            result.transaction.transactionId,
                            FINANCIAL_SNAPSHOT_MISMATCH,
                        )
                        actionRequired += entity.asReconciliationRequired(
                            result.transaction.transactionId,
                            FINANCIAL_SNAPSHOT_MISMATCH,
                        )
                    }
                }

                result?.status == STATUS_FAILED -> {
                    val message = result.error.toMessage()
                    offlineTransactionDao.markFailed(
                        identity.tenantId,
                        identity.userId,
                        entity.clientTransactionId,
                        message,
                    )
                    actionRequired += entity.asFailed(message)
                }

                else -> {
                    offlineTransactionDao.markRetryable(
                        identity.tenantId,
                        identity.userId,
                        listOf(entity.clientTransactionId),
                        INCOMPLETE_RESPONSE,
                    )
                    hasMissingResults = true
                }
            }
        }

        val result = syncResult(
            identity = identity,
            total = pending.size,
            synced = syncedCount,
            failed = actionRequired.size,
        )
        return when {
            actionRequired.isNotEmpty() -> SyncOutcome.ActionRequired(result, actionRequired)
            hasMissingResults -> SyncOutcome.RetryableFailure(
                SyncRetryableFailureReason.SERVER,
                result,
                INCOMPLETE_RESPONSE,
            )
            else -> SyncOutcome.Completed(result)
        }
    }

    private suspend fun syncResult(
        identity: SessionIdentity,
        total: Int = 0,
        synced: Int = 0,
        failed: Int = 0,
    ): SyncResult = SyncResult(total, synced, failed, pendingCount(identity))

    private suspend fun pendingCount(identity: SessionIdentity): Int =
        offlineTransactionDao.observePendingCount(
            identity.tenantId,
            identity.userId,
        ).first()

    private suspend fun currentIdentity(): SessionIdentity? = authSessionDataStore
        .getSession()
        ?.toSessionIdentity()

    private fun decodePayload(entity: OfflineTransactionEntity): OfflineTransactionPayload? =
        runCatching {
            gson.fromJson(entity.payloadJson, OfflineTransactionPayload::class.java)
                ?.takeIf { payload ->
                    payload.version == PAYLOAD_VERSION &&
                        payload.transaction.clientTransactionId == entity.clientTransactionId &&
                        payload.transaction.outletId == entity.outletId &&
                        payload.financialSnapshot.isValidFor(payload.transaction)
                }
        }.getOrNull()

    private fun OfflineFinancialSnapshot.isValidFor(request: CreateTransactionRequest): Boolean {
        if (
            subtotal < 0L || discount < 0L || tax < 0L || total < 0L ||
            paymentAmount < 0L || paymentAmount != request.payment.amount
        ) {
            return false
        }
        val calculatedTotal = runCatching {
            Math.addExact(Math.subtractExact(subtotal, discount), tax)
        }.getOrNull()
        return discount <= subtotal && calculatedTotal == total && paymentAmount >= total
    }

    private fun OfflineFinancialSnapshot.matches(transaction: TransactionDetailResponse): Boolean =
        subtotal == transaction.subtotal &&
            discount == transaction.discount &&
            tax == transaction.tax &&
            total == transaction.total &&
            transaction.payments.singleOrNull()?.amount == paymentAmount

    private suspend fun requireIdentity(): SessionIdentity = currentIdentity()
        ?: error("Authenticated session is required")

    private fun OfflineTransactionEntity.asFailed(error: String): OfflineTransaction = copy(
        status = OfflineTransactionStatus.FAILED,
        lastError = error,
        retryCount = retryCount + 1,
    ).toDomain()

    private fun OfflineTransactionEntity.asReconciliationRequired(
        serverTransactionId: String,
        error: String,
    ): OfflineTransaction = copy(
        status = OfflineTransactionStatus.RECONCILIATION_REQUIRED,
        serverTransactionId = serverTransactionId,
        lastError = error,
        retryCount = retryCount + 1,
    ).toDomain()

    private fun OfflineTransactionEntity.toDomain(): OfflineTransaction {
        val payload = decodePayload(this)
        return OfflineTransaction(
            id = id,
            tenantId = tenantId,
            userId = userId,
            clientTransactionId = clientTransactionId,
            outletId = outletId,
            payloadJson = payloadJson,
            status = status,
            serverTransactionId = serverTransactionId,
            lastError = lastError,
            retryCount = retryCount,
            createdAt = createdAt,
            updatedAt = updatedAt,
            failureType = when (status) {
                OfflineTransactionStatus.RETRYABLE -> OfflineTransactionFailureType.RETRYABLE
                OfflineTransactionStatus.FAILED -> OfflineTransactionFailureType.REJECTED
                OfflineTransactionStatus.RECONCILIATION_REQUIRED ->
                    OfflineTransactionFailureType.RECONCILIATION_REQUIRED
                else -> null
            },
            summary = payload?.toSummary(),
        )
    }

    private fun OfflineTransactionPayload.toSummary(): OfflineTransactionSummary =
        OfflineTransactionSummary(
            itemCount = transaction.items.sumOf { item -> item.quantity },
            subtotal = financialSnapshot.subtotal,
            discount = financialSnapshot.discount,
            tax = financialSnapshot.tax,
            total = financialSnapshot.total,
            paymentMethod = transaction.payment.method,
            paymentAmount = financialSnapshot.paymentAmount,
        )

    private fun SyncTransactionError?.toMessage(): String = when (this) {
        null -> "Transaction sync failed"
        else -> "$errorCode: $message"
    }

    private fun Int.isRetryableHttpStatus(): Boolean =
        this == 408 || this == 425 || this == 429 || this in 500..599

    private companion object {
        const val PAYLOAD_VERSION = 1
        const val MAX_BATCH_SIZE = 100
        const val INSERT_IGNORED = -1L
        const val HTTP_UNAUTHORIZED = 401
        const val STATUS_SYNCED = "SYNCED"
        const val STATUS_FAILED = "FAILED"
        const val INVALID_LOCAL_PAYLOAD = "Invalid local transaction payload"
        const val FINANCIAL_SNAPSHOT_MISMATCH =
            "Synced transaction does not match its checkout financial snapshot"
        const val TRANSPORT_FAILURE = "Unable to reach the sync service"
        const val SERVER_FAILURE = "Sync service is temporarily unavailable"
        const val BUSINESS_FAILURE = "Transaction sync requires action"
        const val EMPTY_RESPONSE = "Sync service returned an empty response"
        const val INCOMPLETE_RESPONSE = "Sync service returned an incomplete response"
    }
}
