package com.kasirkita.pos.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.google.gson.GsonBuilder
import com.kasirkita.pos.core.database.dao.OfflineTransactionDao
import com.kasirkita.pos.core.database.entity.OfflineTransactionEntity
import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.data.api.SyncApi
import com.kasirkita.pos.data.model.CreateTransactionRequest
import com.kasirkita.pos.data.model.SyncTransactionError
import com.kasirkita.pos.data.model.SyncTransactionResult
import com.kasirkita.pos.data.model.SyncTransactionsRequest
import com.kasirkita.pos.data.model.SyncTransactionsResponse
import com.kasirkita.pos.data.model.TransactionDetailResponse
import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.AuthSession
import com.kasirkita.pos.domain.model.OfflineTransactionStatus
import com.kasirkita.pos.domain.model.SyncOutcome
import com.kasirkita.pos.domain.model.SyncRetryableFailureReason
import com.kasirkita.pos.domain.model.UserRole
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.io.IOException
import java.util.UUID

class OfflineSyncRepositoryImplTest {

    private lateinit var dao: FakeOfflineTransactionDao
    private lateinit var api: FakeSyncApi
    private lateinit var sessionStore: AuthSessionDataStore
    private lateinit var repository: OfflineSyncRepositoryImpl
    private val gson = GsonBuilder().serializeNulls().create()

    @Before
    fun setUp() {
        dao = FakeOfflineTransactionDao()
        api = FakeSyncApi()
        sessionStore = AuthSessionDataStore(InMemoryPreferencesDataStore())
        runBlocking { sessionStore.saveSession(session()) }
        repository = OfflineSyncRepositoryImpl(
            offlineTransactionDao = dao,
            syncApi = api,
            gson = gson,
            authSessionDataStore = sessionStore,
        )
    }

    @Test
    fun queueTransaction_createsPendingTransaction() = runBlocking {
        val queued = queueTransaction()

        assertEquals(OfflineTransactionStatus.PENDING, queued.status)
        assertEquals(0, queued.retryCount)
        assertEquals(TENANT_ID, queued.tenantId)
        assertEquals(USER_ID, queued.userId)
        assertEquals(1, dao.pendingCount())
    }

    @Test
    fun pendingTransactions_areIsolatedByTenantAndUser() = runBlocking {
        val current = queueTransaction()
        dao.insert(
            current.toEntity().copy(
                id = "other-account-row",
                tenantId = OTHER_TENANT_ID,
                userId = OTHER_USER_ID,
            ),
        )

        val pending = repository.getPendingTransactions(100).getOrThrow()

        assertEquals(listOf(current.clientTransactionId), pending.map { it.clientTransactionId })
        assertEquals(1, repository.observePendingCount().first())
    }

    @Test
    fun queueSummaryAndFailedLookup_areIsolatedByTenantAndUser() = runBlocking {
        val failed = queueTransaction("failed-current")
        queueTransaction("pending-current")
        dao.markFailed(TENANT_ID, USER_ID, failed.clientTransactionId, "needs action")
        dao.insert(
            failed.toEntity().copy(
                id = "failed-other-account",
                tenantId = OTHER_TENANT_ID,
                userId = OTHER_USER_ID,
                clientTransactionId = "other-account-only",
            ),
        )

        val summary = repository.observeQueueSummary().first()
        val failedTransactions = repository.getFailedTransactions().getOrThrow()

        assertEquals(1, summary.pendingCount)
        assertEquals(1, summary.failedCount)
        assertEquals(listOf("failed-current"), failedTransactions.map { it.clientTransactionId })
        assertEquals("failed-current", repository.getTransaction("failed-current").getOrThrow()?.id)
        assertNull(repository.getTransaction("other-account-only").getOrThrow())
    }

    @Test
    fun retryFailedTransaction_retriesOnlyTheOwnedSelectedTransaction() = runBlocking {
        queueTransaction("failed-selected")
        queueTransaction("failed-untouched")
        dao.markFailed(TENANT_ID, USER_ID, "failed-selected", "first")
        dao.markFailed(TENANT_ID, USER_ID, "failed-untouched", "second")
        api.responder = { request -> Response.success(successResponse(request)) }

        val outcome = repository.retryFailedTransaction("failed-selected").getOrThrow()

        assertTrue(outcome is SyncOutcome.Completed)
        assertEquals(OfflineTransactionStatus.SYNCED, dao.find("failed-selected")?.status)
        assertEquals(OfflineTransactionStatus.FAILED, dao.find("failed-untouched")?.status)
        assertEquals(listOf("failed-selected"), api.lastRequest?.transactions?.map {
            it.clientTransactionId
        })
    }

    @Test
    fun queueTransaction_withoutSession_failsWithoutPersisting() = runBlocking {
        sessionStore.clearSession()

        val result = repository.queueTransaction(
            clientTransactionId = UUID.randomUUID().toString(),
            outletId = OUTLET_ID,
            customerId = null,
            items = listOf(sampleCartItem()),
            paymentAmount = 10_000L,
        )

        assertTrue(result.isFailure)
        assertEquals(0, dao.totalCount())
    }

    @Test
    fun sync_withoutSession_returnsAuthenticationUnavailable() = runBlocking {
        sessionStore.clearSession()

        val outcome = repository.syncPendingTransactions().getOrThrow()

        assertEquals(SyncOutcome.AuthenticationUnavailable, outcome)
        assertEquals(0, api.callCount)
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

        val outcome = repository.syncPendingTransactions().getOrThrow()
        val result = (outcome as SyncOutcome.Completed).result
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

        val outcome = repository.syncPendingTransactions().getOrThrow()
        val result = (outcome as SyncOutcome.ActionRequired).result
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

        val outcome = result.getOrThrow() as SyncOutcome.RetryableFailure
        assertEquals(SyncRetryableFailureReason.TRANSPORT, outcome.reason)
        assertEquals(OfflineTransactionStatus.PENDING, stored?.status)
        assertEquals(1, stored?.retryCount)
        assertEquals(1, dao.pendingCount())
    }

    @Test
    fun serverFailure_isRetryableAndKeepsTransactionPending() = runBlocking {
        val clientTransactionId = UUID.randomUUID().toString()
        queueTransaction(clientTransactionId)
        api.responder = { Response.error(503, "unavailable".toResponseBody()) }

        val outcome = repository.syncPendingTransactions().getOrThrow()
            as SyncOutcome.RetryableFailure

        assertEquals(SyncRetryableFailureReason.SERVER, outcome.reason)
        assertEquals(OfflineTransactionStatus.PENDING, dao.find(clientTransactionId)?.status)
        assertEquals(1, dao.find(clientTransactionId)?.retryCount)
    }

    @Test
    fun sync_sendsAtMostOneHundredTransactions() = runBlocking {
        repeat(101) {
            queueTransaction(UUID.randomUUID().toString())
        }
        api.responder = { request ->
            Response.success(successResponse(request))
        }

        val outcome = repository.syncPendingTransactions().getOrThrow()
        val result = (outcome as SyncOutcome.Completed).result

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
        val secondOutcome = repository.syncPendingTransactions().getOrThrow()
        val secondResult = (secondOutcome as SyncOutcome.Completed).result

        assertEquals(1, api.callCount)
        assertEquals(0, secondResult.total)
    }

    @Test
    fun concurrentSyncRequests_doNotOverlap() = runBlocking {
        queueTransaction("serialized-client")
        val requestStarted = CompletableDeferred<Unit>()
        val releaseRequest = CompletableDeferred<Unit>()
        api.responder = { request ->
            requestStarted.complete(Unit)
            releaseRequest.await()
            Response.success(successResponse(request))
        }

        coroutineScope {
            val first = async { repository.syncPendingTransactions().getOrThrow() }
            requestStarted.await()
            val second = async { repository.syncPendingTransactions().getOrThrow() }
            yield()

            assertEquals(1, api.callCount)
            releaseRequest.complete(Unit)
            assertTrue(first.await() is SyncOutcome.Completed)
            assertTrue(second.await() is SyncOutcome.Completed)
        }

        assertEquals(1, api.callCount)
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
        transaction = TransactionDetailResponse(
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
        private val revision = MutableStateFlow(0)
        var insertFailure: Throwable? = null

        override suspend fun insert(transaction: OfflineTransactionEntity): Long {
            insertFailure?.let { throwable -> throw throwable }
            val duplicate = records.values.any {
                it.tenantId == transaction.tenantId &&
                    it.userId == transaction.userId &&
                    it.clientTransactionId == transaction.clientTransactionId
            }
            if (duplicate) return -1L

            records[transaction.id] = transaction
            revision.value++
            return records.size.toLong()
        }

        override suspend fun getByClientTransactionId(
            tenantId: String,
            userId: String,
            clientTransactionId: String,
        ): OfflineTransactionEntity? = find(tenantId, userId, clientTransactionId)

        override suspend fun getPendingTransactions(
            tenantId: String,
            userId: String,
            limit: Int,
        ): List<OfflineTransactionEntity> = records.values
            .asSequence()
            .filter { entity ->
                entity.tenantId == tenantId &&
                    entity.userId == userId &&
                    entity.status == OfflineTransactionStatus.PENDING
            }
            .sortedBy(OfflineTransactionEntity::createdAt)
            .take(limit)
            .toList()

        override suspend fun getFailedTransactions(
            tenantId: String,
            userId: String,
            limit: Int,
        ): List<OfflineTransactionEntity> = records.values
            .asSequence()
            .filter { entity ->
                entity.tenantId == tenantId &&
                    entity.userId == userId &&
                    entity.status == OfflineTransactionStatus.FAILED
            }
            .sortedBy(OfflineTransactionEntity::updatedAt)
            .take(limit)
            .toList()

        override fun observePendingCount(tenantId: String, userId: String): Flow<Int> =
            revision.map {
                records.values.count { entity ->
                    entity.tenantId == tenantId &&
                        entity.userId == userId &&
                        entity.status == OfflineTransactionStatus.PENDING
                }
            }

        override fun observeCountByStatus(
            tenantId: String,
            userId: String,
            status: String,
        ): Flow<Int> = revision.map {
            records.values.count { entity ->
                entity.tenantId == tenantId &&
                    entity.userId == userId &&
                    entity.status == status
            }
        }

        override suspend fun markSynced(
            tenantId: String,
            userId: String,
            clientTransactionId: String,
            serverTransactionId: String,
        ) {
            update(tenantId, userId, clientTransactionId) { entity ->
                entity.copy(
                    status = OfflineTransactionStatus.SYNCED,
                    serverTransactionId = serverTransactionId,
                    lastError = null,
                )
            }
        }

        override suspend fun markFailed(
            tenantId: String,
            userId: String,
            clientTransactionId: String,
            error: String,
        ) {
            update(tenantId, userId, clientTransactionId) { entity ->
                entity.copy(
                    status = OfflineTransactionStatus.FAILED,
                    lastError = error,
                    retryCount = entity.retryCount + 1,
                )
            }
        }

        override suspend fun incrementRetry(
            tenantId: String,
            userId: String,
            clientTransactionIds: List<String>,
        ) {
            clientTransactionIds.forEach { clientTransactionId ->
                update(tenantId, userId, clientTransactionId) { entity ->
                    if (entity.status == OfflineTransactionStatus.PENDING) {
                        entity.copy(retryCount = entity.retryCount + 1)
                    } else {
                        entity
                    }
                }
            }
        }

        override suspend fun retryFailedTransactions(tenantId: String, userId: String) {
            records.replaceAll { _, entity ->
                if (
                    entity.tenantId == tenantId &&
                    entity.userId == userId &&
                    entity.status == OfflineTransactionStatus.FAILED
                ) {
                    entity.copy(
                        status = OfflineTransactionStatus.PENDING,
                        lastError = null,
                    )
                } else {
                    entity
                }
            }
            revision.value++
        }

        override suspend fun retryFailedTransaction(
            tenantId: String,
            userId: String,
            clientTransactionId: String,
        ) {
            update(tenantId, userId, clientTransactionId) { entity ->
                if (entity.status == OfflineTransactionStatus.FAILED) {
                    entity.copy(
                        status = OfflineTransactionStatus.PENDING,
                        lastError = null,
                    )
                } else {
                    entity
                }
            }
        }

        override suspend fun deleteSynced(tenantId: String, userId: String) {
            records.entries.removeAll { (_, entity) ->
                entity.tenantId == tenantId &&
                    entity.userId == userId &&
                    entity.status == OfflineTransactionStatus.SYNCED
            }
            revision.value++
        }

        fun find(clientTransactionId: String): OfflineTransactionEntity? =
            find(TENANT_ID, USER_ID, clientTransactionId)

        fun pendingCount(): Int = records.values.count { entity ->
            entity.tenantId == TENANT_ID &&
                entity.userId == USER_ID &&
                entity.status == OfflineTransactionStatus.PENDING
        }

        fun totalCount(): Int = records.size

        private fun find(
            tenantId: String,
            userId: String,
            clientTransactionId: String,
        ): OfflineTransactionEntity? = records.values.firstOrNull { entity ->
            entity.tenantId == tenantId &&
                entity.userId == userId &&
                entity.clientTransactionId == clientTransactionId
        }

        private fun update(
            tenantId: String,
            userId: String,
            clientTransactionId: String,
            transform: (OfflineTransactionEntity) -> OfflineTransactionEntity,
        ) {
            val entry = records.entries.firstOrNull {
                it.value.tenantId == tenantId &&
                    it.value.userId == userId &&
                    it.value.clientTransactionId == clientTransactionId
            } ?: return
            records[entry.key] = transform(entry.value)
            revision.value++
        }
    }

    private fun session() = AuthSession(
        userId = USER_ID,
        userName = "Kasir Utama",
        tenantId = TENANT_ID,
        role = UserRole.CASHIER,
        outletId = OUTLET_ID,
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresAt = Long.MAX_VALUE,
        deviceId = "device-id",
    )

    private fun com.kasirkita.pos.domain.model.OfflineTransaction.toEntity() =
        OfflineTransactionEntity(
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

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences = transform(state.value).also { updated ->
            state.value = updated
        }
    }

    private companion object {
        const val OUTLET_ID = "ce591e13-afd5-46ee-b3a3-5a0fdc51c2ce"
        const val PRODUCT_ID = "c3c3c3c3-aaaa-4444-bbbb-222222222222"
        const val TENANT_ID = "tenant-id"
        const val OTHER_TENANT_ID = "other-tenant-id"
        const val USER_ID = "8f1fbf18-ed1e-4db8-a78d-857238e08e62"
        const val OTHER_USER_ID = "other-user-id"
    }
}
