package com.kasirkita.pos.data.repository

import com.google.gson.Gson
import com.kasirkita.pos.core.database.dao.OfflineTransactionDao
import com.kasirkita.pos.core.database.entity.OfflineTransactionEntity
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineSyncRepositoryImpl @Inject constructor(
    private val offlineTransactionDao: OfflineTransactionDao,
    private val syncApi: SyncApi,
    private val gson: Gson,
) : OfflineSyncRepository {

    override suspend fun queueTransaction(
        clientTransactionId: String,
        outletId: String,
        customerId: String?,
        items: List<CartItem>,
        paymentAmount: Long,
    ): Result<OfflineTransaction> = runCatching {
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
            offlineTransactionDao.getByClientTransactionId(clientTransactionId)
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
        if (limit <= 0) {
            emptyList()
        } else {
            offlineTransactionDao
                .getPendingTransactions(limit.coerceAtMost(MAX_BATCH_SIZE))
                .map { entity -> entity.toDomain() }
        }
    }

    override fun observePendingCount(): Flow<Int> =
        offlineTransactionDao.observePendingCount()

    override suspend fun syncPendingTransactions(): Result<SyncResult> {
        val pending = try {
            offlineTransactionDao.getPendingTransactions(MAX_BATCH_SIZE)
        } catch (throwable: Throwable) {
            return Result.failure(throwable)
        }
        if (pending.isEmpty()) {
            return Result.success(
                SyncResult(
                    total = 0,
                    synced = 0,
                    failed = 0,
                    pending = pendingCount(),
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
                    pending = pendingCount(),
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
            offlineTransactionDao.incrementRetry(clientIds)
            return Result.failure(throwable)
        }

        if (!response.isSuccessful) {
            offlineTransactionDao.incrementRetry(clientIds)
            return Result.failure(HttpException(response))
        }

        val responseBody = response.body()
        if (responseBody == null) {
            offlineTransactionDao.incrementRetry(clientIds)
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
                        clientTransactionId = entity.clientTransactionId,
                        serverTransactionId = result.transaction.transactionId,
                    )
                    syncedCount++
                }

                result?.status == STATUS_FAILED -> {
                    offlineTransactionDao.markFailed(
                        clientTransactionId = entity.clientTransactionId,
                        error = result.error.toMessage(),
                    )
                    failedCount++
                }

                else -> {
                    offlineTransactionDao.incrementRetry(
                        listOf(entity.clientTransactionId),
                    )
                }
            }
        }

        return Result.success(
            SyncResult(
                total = pending.size,
                synced = syncedCount,
                failed = failedCount,
                pending = pendingCount(),
            ),
        )
    }

    override suspend fun retryFailedTransactions(): Result<SyncResult> = runCatching {
        offlineTransactionDao.retryFailedTransactions()
    }.fold(
        onSuccess = { syncPendingTransactions() },
        onFailure = Result.Companion::failure,
    )

    private suspend fun pendingCount(): Int =
        offlineTransactionDao.observePendingCount().first()

    private fun OfflineTransactionEntity.toDomain(): OfflineTransaction = OfflineTransaction(
        id = id,
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
