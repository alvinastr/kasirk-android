package com.kasirkita.pos.data.repository

import com.google.gson.Gson
import com.kasirkita.pos.core.database.dao.OfflineTransactionDao
import com.kasirkita.pos.core.database.entity.OfflineTransactionEntity
import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.core.datastore.SessionIdentity
import com.kasirkita.pos.core.datastore.toSessionIdentity
import com.kasirkita.pos.data.api.SyncApi
import com.kasirkita.pos.data.model.CreateTransactionRequest
import com.kasirkita.pos.data.model.SyncTransactionError
import com.kasirkita.pos.data.model.SyncTransactionsRequest
import com.kasirkita.pos.data.model.createTransactionRequest
import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.OfflineTransaction
import com.kasirkita.pos.domain.model.OfflineTransactionStatus
import com.kasirkita.pos.domain.model.SyncResult
import com.kasirkita.pos.domain.repository.OfflineSyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineSyncRepositoryImpl @Inject constructor(
    private val offlineTransactionDao: OfflineTransactionDao,
    private val syncApi: SyncApi,
    private val gson: Gson,
    private val authSessionDataStore: AuthSessionDataStore,
) : OfflineSyncRepository {

    override suspend fun queueTransaction(
        clientTransactionId: String,
        outletId: String,
        customerId: String?,
        items: List<CartItem>,
        paymentAmount: Long,
    ): Result<OfflineTransaction> = runCatching {
        val identity = requireIdentity()
        val request = createTransactionRequest(
            clientTransactionId = clientTransactionId,
            outletId = outletId,
            customerId = customerId,
            items = items,
            paymentAmount = paymentAmount,
        )
        val now = System.currentTimeMillis()
        val entity = OfflineTransactionEntity(
            id = clientTransactionId,
            tenantId = identity.tenantId,
            userId = identity.userId,
            clientTransactionId = clientTransactionId,
            outletId = outletId,
            payloadJson = gson.toJson(request),
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
            offlineTransactionDao
                .getPendingTransactions(
                    tenantId = identity.tenantId,
                    userId = identity.userId,
                    limit = limit.coerceAtMost(MAX_BATCH_SIZE),
                )
                .map { entity -> entity.toDomain() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observePendingCount(): Flow<Int> =
        authSessionDataStore.sessionFlow
            .map { session -> session?.toSessionIdentity() }
            .distinctUntilChanged()
            .flatMapLatest { identity ->
                if (identity == null) {
                    flowOf(0)
                } else {
                    offlineTransactionDao.observePendingCount(
                        tenantId = identity.tenantId,
                        userId = identity.userId,
                    )
                }
            }

    override suspend fun syncPendingTransactions(): Result<SyncResult> {
        val identity = try {
            requireIdentity()
        } catch (throwable: Throwable) {
            return Result.failure(throwable)
        }
        return syncPendingTransactions(identity)
    }

    private suspend fun syncPendingTransactions(
        identity: SessionIdentity,
    ): Result<SyncResult> {
        val pending = try {
            offlineTransactionDao.getPendingTransactions(
                tenantId = identity.tenantId,
                userId = identity.userId,
                limit = MAX_BATCH_SIZE,
            )
        } catch (throwable: Throwable) {
            return Result.failure(throwable)
        }
        if (pending.isEmpty()) {
            return Result.success(
                SyncResult(
                    total = 0,
                    synced = 0,
                    failed = 0,
                    pending = pendingCount(identity),
                ),
            )
        }

        val validPayloads = mutableListOf<Pair<OfflineTransactionEntity, CreateTransactionRequest>>()
        var failedCount = 0

        pending.forEach { entity ->
            val request = runCatching {
                gson.fromJson(entity.payloadJson, CreateTransactionRequest::class.java)
            }.getOrNull()

            if (request == null || request.clientTransactionId != entity.clientTransactionId) {
                offlineTransactionDao.markFailed(
                    tenantId = identity.tenantId,
                    userId = identity.userId,
                    clientTransactionId = entity.clientTransactionId,
                    error = INVALID_LOCAL_PAYLOAD,
                )
                failedCount++
            } else {
                validPayloads += entity to request
            }
        }

        if (validPayloads.isEmpty()) {
            return Result.success(
                SyncResult(
                    total = pending.size,
                    synced = 0,
                    failed = failedCount,
                    pending = pendingCount(identity),
                ),
            )
        }

        val clientIds = validPayloads.map { (entity) -> entity.clientTransactionId }
        val response = try {
            syncApi.syncTransactions(
                SyncTransactionsRequest(
                    transactions = validPayloads.map { (_, request) -> request },
                ),
            )
        } catch (throwable: Throwable) {
            offlineTransactionDao.incrementRetry(
                tenantId = identity.tenantId,
                userId = identity.userId,
                clientTransactionIds = clientIds,
            )
            return Result.failure(throwable)
        }

        if (!response.isSuccessful) {
            offlineTransactionDao.incrementRetry(
                tenantId = identity.tenantId,
                userId = identity.userId,
                clientTransactionIds = clientIds,
            )
            return Result.failure(HttpException(response))
        }

        val responseBody = response.body()
        if (responseBody == null) {
            offlineTransactionDao.incrementRetry(
                tenantId = identity.tenantId,
                userId = identity.userId,
                clientTransactionIds = clientIds,
            )
            return Result.failure(IllegalStateException("Sync response body is empty"))
        }

        val resultsByClientId = responseBody.results.associateBy { result ->
            result.clientTransactionId
        }
        var syncedCount = 0

        validPayloads.forEach { (entity) ->
            val result = resultsByClientId[entity.clientTransactionId]
            when {
                result?.status == STATUS_SYNCED && result.transaction != null -> {
                    offlineTransactionDao.markSynced(
                        tenantId = identity.tenantId,
                        userId = identity.userId,
                        clientTransactionId = entity.clientTransactionId,
                        serverTransactionId = result.transaction.transactionId,
                    )
                    syncedCount++
                }

                result?.status == STATUS_FAILED -> {
                    offlineTransactionDao.markFailed(
                        tenantId = identity.tenantId,
                        userId = identity.userId,
                        clientTransactionId = entity.clientTransactionId,
                        error = result.error.toMessage(),
                    )
                    failedCount++
                }

                else -> {
                    offlineTransactionDao.incrementRetry(
                        tenantId = identity.tenantId,
                        userId = identity.userId,
                        clientTransactionIds = listOf(entity.clientTransactionId),
                    )
                }
            }
        }

        return Result.success(
            SyncResult(
                total = pending.size,
                synced = syncedCount,
                failed = failedCount,
                pending = pendingCount(identity),
            ),
        )
    }

    override suspend fun retryFailedTransactions(): Result<SyncResult> = runCatching {
        val identity = requireIdentity()
        offlineTransactionDao.retryFailedTransactions(
            tenantId = identity.tenantId,
            userId = identity.userId,
        )
        identity
    }.fold(
        onSuccess = { identity -> syncPendingTransactions(identity) },
        onFailure = Result.Companion::failure,
    )

    private suspend fun pendingCount(identity: SessionIdentity): Int =
        offlineTransactionDao.observePendingCount(
            tenantId = identity.tenantId,
            userId = identity.userId,
        ).first()

    private suspend fun requireIdentity(): SessionIdentity = authSessionDataStore
        .getSession()
        ?.toSessionIdentity()
        ?: error("Authenticated session is required")

    private fun OfflineTransactionEntity.toDomain(): OfflineTransaction = OfflineTransaction(
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
    )

    private fun SyncTransactionError?.toMessage(): String = when (this) {
        null -> "Transaction sync failed"
        else -> "$errorCode: $message"
    }

    private companion object {
        const val MAX_BATCH_SIZE = 100
        const val INSERT_IGNORED = -1L
        const val STATUS_SYNCED = "SYNCED"
        const val STATUS_FAILED = "FAILED"
        const val INVALID_LOCAL_PAYLOAD = "Invalid local transaction payload"
    }
}
