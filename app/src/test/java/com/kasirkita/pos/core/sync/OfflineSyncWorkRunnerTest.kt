package com.kasirkita.pos.core.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.domain.model.AuthSession
import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.OfflineFinancialSnapshot
import com.kasirkita.pos.domain.model.OfflineQueueSummary
import com.kasirkita.pos.domain.model.OfflineTransaction
import com.kasirkita.pos.domain.model.SyncOutcome
import com.kasirkita.pos.domain.model.SyncResult
import com.kasirkita.pos.domain.model.SyncRetryableFailureReason
import com.kasirkita.pos.domain.model.UserRole
import com.kasirkita.pos.domain.repository.OfflineSyncRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OfflineSyncWorkRunnerTest {

    private lateinit var sessionStore: AuthSessionDataStore
    private lateinit var repository: FakeOfflineSyncRepository
    private lateinit var runner: OfflineSyncWorkRunner

    @Before
    fun setUp() {
        sessionStore = AuthSessionDataStore(InMemoryPreferencesDataStore())
        repository = FakeOfflineSyncRepository()
        runner = OfflineSyncWorkRunner(sessionStore, repository)
    }

    @Test
    fun matchingAccount_completedSync_succeeds() = runBlocking {
        sessionStore.saveSession(session())
        repository.syncResult = Result.success(SyncOutcome.Completed(SYNC_RESULT))

        val result = runner.run(TENANT_ID, USER_ID)

        assertEquals(OfflineSyncWorkResult.SUCCESS, result)
        assertEquals(TENANT_ID to USER_ID, repository.requestedAccount)
    }

    @Test
    fun previousAccountWork_isIgnored() = runBlocking {
        sessionStore.saveSession(session())

        val result = runner.run(OTHER_TENANT_ID, OTHER_USER_ID)

        assertEquals(OfflineSyncWorkResult.SUCCESS, result)
        assertEquals(null, repository.requestedAccount)
    }

    @Test
    fun loggedOutWork_isIgnored() = runBlocking {
        val result = runner.run(TENANT_ID, USER_ID)

        assertEquals(OfflineSyncWorkResult.SUCCESS, result)
        assertEquals(null, repository.requestedAccount)
    }

    @Test
    fun retryableFailure_retries() = runBlocking {
        sessionStore.saveSession(session())
        repository.syncResult = Result.success(
            SyncOutcome.RetryableFailure(
                reason = SyncRetryableFailureReason.TRANSPORT,
                result = SYNC_RESULT,
                message = "offline",
            ),
        )

        assertEquals(
            OfflineSyncWorkResult.RETRY,
            runner.run(TENANT_ID, USER_ID),
        )
    }

    @Test
    fun businessFailure_doesNotRetry() = runBlocking {
        sessionStore.saveSession(session())
        repository.syncResult = Result.success(
            SyncOutcome.ActionRequired(
                result = SYNC_RESULT.copy(failed = 1),
                failedTransactions = emptyList(),
            ),
        )

        assertEquals(
            OfflineSyncWorkResult.SUCCESS,
            runner.run(TENANT_ID, USER_ID),
        )
    }

    @Test
    fun unexpectedRepositoryFailure_failsWork() = runBlocking {
        sessionStore.saveSession(session())
        repository.syncResult = Result.failure(IOException("storage unavailable"))

        assertEquals(
            OfflineSyncWorkResult.FAILURE,
            runner.run(TENANT_ID, USER_ID),
        )
    }

    @Test
    fun missingIdentityInput_failsWork() = runBlocking {
        assertEquals(OfflineSyncWorkResult.FAILURE, runner.run(null, USER_ID))
        assertEquals(OfflineSyncWorkResult.FAILURE, runner.run(TENANT_ID, ""))
    }

    private fun session() = AuthSession(
        userId = USER_ID,
        userName = "Cashier",
        tenantId = TENANT_ID,
        role = UserRole.CASHIER,
        outletId = "outlet-id",
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresAt = Long.MAX_VALUE,
        deviceId = "device-id",
    )

    private class FakeOfflineSyncRepository : OfflineSyncRepository {
        var syncResult: Result<SyncOutcome> = Result.success(
            SyncOutcome.Completed(SYNC_RESULT),
        )
        var requestedAccount: Pair<String, String>? = null

        override suspend fun syncPendingTransactionsForAccount(
            tenantId: String,
            userId: String,
        ): Result<SyncOutcome> {
            requestedAccount = tenantId to userId
            return syncResult
        }

        override fun observeQueueSummary(): Flow<OfflineQueueSummary> =
            flowOf(OfflineQueueSummary(0, 0))

        override fun observePendingCount(): Flow<Int> = flowOf(0)

        override suspend fun queueTransaction(
            clientTransactionId: String,
            outletId: String,
            customerId: String?,
            items: List<CartItem>,
            paymentAmount: Long,
            financialSnapshot: OfflineFinancialSnapshot,
        ): Result<OfflineTransaction> = error("Not used")

        override suspend fun getPendingTransactions(
            limit: Int,
        ): Result<List<OfflineTransaction>> = error("Not used")

        override suspend fun getTransaction(
            clientTransactionId: String,
        ): Result<OfflineTransaction?> = error("Not used")

        override suspend fun getFailedTransactions(
            limit: Int,
        ): Result<List<OfflineTransaction>> = error("Not used")

        override suspend fun getActionRequiredTransactions(
            limit: Int,
        ): Result<List<OfflineTransaction>> = error("Not used")

        override suspend fun syncPendingTransactions(): Result<SyncOutcome> = error("Not used")

        override suspend fun retryFailedTransaction(
            clientTransactionId: String,
        ): Result<SyncOutcome> = error("Not used")

        override suspend fun retryFailedTransactions(): Result<SyncOutcome> = error("Not used")
    }

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
        const val TENANT_ID = "tenant-id"
        const val USER_ID = "user-id"
        const val OTHER_TENANT_ID = "other-tenant-id"
        const val OTHER_USER_ID = "other-user-id"
        val SYNC_RESULT = SyncResult(total = 1, synced = 1, failed = 0, pending = 0)
    }
}
