package com.kasirkita.pos.data.repository

import com.google.gson.GsonBuilder
import com.kasirkita.pos.core.database.dao.OfflineTransactionDao
import com.kasirkita.pos.core.database.entity.OfflineTransactionEntity
import com.kasirkita.pos.data.api.SyncApi
import com.kasirkita.pos.data.model.CreateTransactionRequest
import com.kasirkita.pos.data.model.SyncTransactionError
import com.kasirkita.pos.data.model.SyncTransactionResult
import com.kasirkita.pos.data.model.SyncTransactionsRequest
import com.kasirkita.pos.data.model.SyncTransactionsResponse
import com.kasirkita.pos.data.model.TransactionResponse
import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.OfflineTransactionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.util.UUID

class OfflineSyncRepositoryImplTest {

    private lateinit var dao: FakeOfflineTransactionDao
    private lateinit var api: FakeSyncApi
    private lateinit var repository: OfflineSyncRepositoryImpl
    private val gson = GsonBuilder().serializeNulls().create()

    @Before
    fun setUp() {
        dao = FakeOfflineTransactionDao()
        api = FakeSyncApi()
        repository = OfflineSyncRepositoryImpl(
            offlineTransactionDao = dao,
            syncApi = api,
            gson = gson,
        )
    }

    @Test
    fun queueTransaction_createsPendingTransaction() = runBlocking {
        val queued = queueTransaction()

        assertEquals(OfflineTransactionStatus.PENDING, queued.status)
        assertEquals(0, queued.retryCount)
        assertEquals(1, dao.pendingCount())
    }

    @Test
    fun queueTransaction_persistsTheCompleteRequestWithTheSameClientId() = runBlocking {
        val clientTransactionId = UUID.randomUUID().toString()
        val queued = queueTransaction(clientTransactionId)
        val payload = gson.fromJson(
            queued.payloadJson,
            CreateTransactionRequest::class.java,
        )

        assertEquals(clientTransactionId, queued.clientTransactionId)
        assertEquals(clientTransactionId, payload.clientTransactionId)
        assertEquals(OUTLET_ID, payload.outletId)
        assertNull(payload.customerId)
        assertEquals(1, payload.items.size)
        assertEquals(PRODUCT_ID, payload.items.single().productId)
        assertEquals(1, payload.items.single().quantity)
        assertEquals("CASH", payload.payment.method)
        assertEquals(10_000L, payload.payment.amount)
    }

    @Test
    fun queueTransaction_whenPersistenceFails_returnsFailureWithoutPendingRecord() = runBlocking {
        dao.insertFailure = IllegalStateException("disk full")

        val result = repository.queueTransaction(
            clientTransactionId = UUID.randomUUID().toString(),
            outletId = OUTLET_ID,
            customerId = null,
            items = listOf(sampleCartItem()),
            paymentAmount = 10_000L,
        )

        assertTrue(result.isFailure)
        assertEquals(0, dao.pendingCount())
    }

    @Test
    fun syncedResponse_marksTransactionAsSynced() = runBlocking {
        val clientTransactionId = UUID.randomUUID().toString()
        queueTransaction(clientTransactionId)
        api.responder = { request ->
            Response.success(successResponse(request))
        }

        val result = repository.syncPendingTransactions().getOrThrow()
        val stored = dao.find(clientTransactionId)

        assertEquals(1, result.synced)
        assertEquals(OfflineTransactionStatus.SYNCED, stored?.status)
        assertNotNull(stored?.serverTransactionId)
        assertEquals(0, dao.pendingCount())
    }

    @Test
    fun failedResponse_marksTransactionAsFailed() = runBlocking {
        val clientTransactionId = UUID.randomUUID().toString()
        queueTransaction(clientTransactionId)
        api.responder = {
            Response.success(
                SyncTransactionsResponse(
                    total = 1,
                    synced = 0,
                    failed = 1,
                    results = listOf(
                        SyncTransactionResult(
                            clientTransactionId = clientTransactionId,
                            status = "FAILED",
                            transaction = null,
                            error = SyncTransactionError(
                                statusCode = 409,
                                errorCode = "INSUFFICIENT_STOCK",
                                message = "Insufficient stock",
                            ),
                        ),
                    ),
                ),
            )
        }

        val result = repository.syncPendingTransactions().getOrThrow()
        val stored = dao.find(clientTransactionId)

        assertEquals(1, result.failed)
        assertEquals(OfflineTransactionStatus.FAILED, stored?.status)
        assertEquals(1, stored?.retryCount)
        assertTrue(stored?.lastError?.contains("INSUFFICIENT_STOCK") == true)
    }

    @Test
    fun networkFailure_keepsTransactionPending() = runBlocking {
        val clientTransactionId = UUID.randomUUID().toString()
        queueTransaction(clientTransactionId)
        api.responder = { throw IOException("offline") }

        val result = repository.syncPendingTransactions()
        val stored = dao.find(clientTransactionId)

        assertTrue(result.isFailure)
        assertEquals(OfflineTransactionStatus.PENDING, stored?.status)
        assertEquals(1, stored?.retryCount)
        assertEquals(1, dao.pendingCount())
    }

    @Test
    fun sync_sendsAtMostOneHundredTransactions() = runBlocking {
        repeat(101) {
            queueTransaction(UUID.randomUUID().toString())
        }
        api.responder = { request ->
            Response.success(successResponse(request))
        }

        val result = repository.syncPendingTransactions().getOrThrow()

        assertEquals(100, api.lastRequest?.transactions?.size)
        assertEquals(100, result.synced)
        assertEquals(1, dao.pendingCount())
    }

    @Test
    fun syncedTransaction_isNotSentAgain() = runBlocking {
        queueTransaction()
        api.responder = { request ->
            Response.success(successResponse(request))
        }

        repository.syncPendingTransactions().getOrThrow()
        val secondResult = repository.syncPendingTransactions().getOrThrow()

        assertEquals(1, api.callCount)
        assertEquals(0, secondResult.total)
    }

    @Test
    fun sync_matchesShuffledResultsByClientTransactionId() = runBlocking {
        val firstClientId = UUID.randomUUID().toString()
        val secondClientId = UUID.randomUUID().toString()
        val firstServerId = UUID.randomUUID().toString()
        val secondServerId = UUID.randomUUID().toString()
        queueTransaction(firstClientId)
        queueTransaction(secondClientId)
        api.responder = { request ->
            val byClientId = request.transactions.associateBy {
                it.clientTransactionId
            }
            Response.success(
                SyncTransactionsResponse(
                    total = 2,
                    synced = 2,
                    failed = 0,
                    results = listOf(
                        successResult(
                            transaction = requireNotNull(byClientId[secondClientId]),
                            serverTransactionId = secondServerId,
                        ),
                        successResult(
                            transaction = requireNotNull(byClientId[firstClientId]),
                            serverTransactionId = firstServerId,
                        ),
                    ),
                ),
            )
        }

        repository.syncPendingTransactions().getOrThrow()

        assertEquals(firstServerId, dao.find(firstClientId)?.serverTransactionId)
        assertEquals(secondServerId, dao.find(secondClientId)?.serverTransactionId)
    }

    private suspend fun queueTransaction(
        clientTransactionId: String = UUID.randomUUID().toString(),
    ) = repository.queueTransaction(
        clientTransactionId = clientTransactionId,
        outletId = OUTLET_ID,
        customerId = null,
        items = listOf(sampleCartItem()),
        paymentAmount = 10_000L,
    ).getOrThrow()

    private fun successResponse(
        request: SyncTransactionsRequest,
    ): SyncTransactionsResponse = SyncTransactionsResponse(
        total = request.transactions.size,
        synced = request.transactions.size,
        failed = 0,
        results = request.transactions.map { transaction ->
            successResult(
                transaction = transaction,
                serverTransactionId = UUID.randomUUID().toString(),
            )
        },
    )

    private fun successResult(
        transaction: CreateTransactionRequest,
        serverTransactionId: String,
    ): SyncTransactionResult = SyncTransactionResult(
        clientTransactionId = transaction.clientTransactionId,
        status = "SYNCED",
        transaction = TransactionResponse(
            transactionId = serverTransactionId,
            clientTransactionId = transaction.clientTransactionId,
            outletId = transaction.outletId,
            userId = USER_ID,
            customerId = transaction.customerId,
            status = "COMPLETED",
            subtotal = 10_000L,
            discount = 0L,
            tax = 0L,
            total = 10_000L,
            items = emptyList(),
            payments = emptyList(),
            change = 0L,
            createdAt = "2026-09-17T00:00:00.000Z",
        ),
        error = null,
    )

    private fun sampleCartItem(): CartItem = CartItem(
        productId = PRODUCT_ID,
        name = "Kopi",
        sku = "KOPI",
        price = 10_000L,
        quantity = 1,
    )

    private class FakeSyncApi : SyncApi {
        var callCount: Int = 0
        var lastRequest: SyncTransactionsRequest? = null
        var responder: suspend (SyncTransactionsRequest) -> Response<SyncTransactionsResponse> = {
            error("Responder has not been configured")
        }

        override suspend fun syncTransactions(
            request: SyncTransactionsRequest,
        ): Response<SyncTransactionsResponse> {
            callCount++
            lastRequest = request
            return responder(request)
        }
    }

    private class FakeOfflineTransactionDao : OfflineTransactionDao {
        private val records = linkedMapOf<String, OfflineTransactionEntity>()
        private val pendingCount = MutableStateFlow(0)
        var insertFailure: Throwable? = null

        override suspend fun insert(transaction: OfflineTransactionEntity): Long {
            insertFailure?.let { throwable -> throw throwable }
            val duplicate = records.values.any {
                it.id == transaction.id ||
                    it.clientTransactionId == transaction.clientTransactionId
            }
            if (duplicate) return -1L

            records[transaction.id] = transaction
            updatePendingCount()
            return records.size.toLong()
        }

        override suspend fun getByClientTransactionId(
            clientTransactionId: String,
        ): OfflineTransactionEntity? = find(clientTransactionId)

        override suspend fun getPendingTransactions(
            limit: Int,
        ): List<OfflineTransactionEntity> = records.values
            .asSequence()
            .filter { it.status == OfflineTransactionStatus.PENDING }
            .sortedBy(OfflineTransactionEntity::createdAt)
            .take(limit)
            .toList()

        override fun observePendingCount(): Flow<Int> = pendingCount

        override suspend fun markSynced(
            clientTransactionId: String,
            serverTransactionId: String,
        ) {
            update(clientTransactionId) { entity ->
                entity.copy(
                    status = OfflineTransactionStatus.SYNCED,
                    serverTransactionId = serverTransactionId,
                    lastError = null,
                )
            }
        }

        override suspend fun markFailed(
            clientTransactionId: String,
            error: String,
        ) {
            update(clientTransactionId) { entity ->
                entity.copy(
                    status = OfflineTransactionStatus.FAILED,
                    lastError = error,
                    retryCount = entity.retryCount + 1,
                )
            }
        }

        override suspend fun incrementRetry(clientTransactionIds: List<String>) {
            clientTransactionIds.forEach { clientTransactionId ->
                update(clientTransactionId) { entity ->
                    if (entity.status == OfflineTransactionStatus.PENDING) {
                        entity.copy(retryCount = entity.retryCount + 1)
                    } else {
                        entity
                    }
                }
            }
        }

        override suspend fun retryFailedTransactions() {
            records.replaceAll { _, entity ->
                if (entity.status == OfflineTransactionStatus.FAILED) {
                    entity.copy(
                        status = OfflineTransactionStatus.PENDING,
                        lastError = null,
                    )
                } else {
                    entity
                }
            }
            updatePendingCount()
        }

        override suspend fun deleteSynced() {
            records.entries.removeAll { (_, entity) ->
                entity.status == OfflineTransactionStatus.SYNCED
            }
            updatePendingCount()
        }

        fun find(clientTransactionId: String): OfflineTransactionEntity? =
            records.values.firstOrNull {
                it.clientTransactionId == clientTransactionId
            }

        fun pendingCount(): Int = pendingCount.value

        private fun update(
            clientTransactionId: String,
            transform: (OfflineTransactionEntity) -> OfflineTransactionEntity,
        ) {
            val entry = records.entries.firstOrNull {
                it.value.clientTransactionId == clientTransactionId
            } ?: return
            records[entry.key] = transform(entry.value)
            updatePendingCount()
        }

        private fun updatePendingCount() {
            pendingCount.value = records.values.count {
                it.status == OfflineTransactionStatus.PENDING
            }
        }
    }

    private companion object {
        const val OUTLET_ID = "ce591e13-afd5-46ee-b3a3-5a0fdc51c2ce"
        const val PRODUCT_ID = "c3c3c3c3-aaaa-4444-bbbb-222222222222"
        const val USER_ID = "8f1fbf18-ed1e-4db8-a78d-857238e08e62"
    }
}
