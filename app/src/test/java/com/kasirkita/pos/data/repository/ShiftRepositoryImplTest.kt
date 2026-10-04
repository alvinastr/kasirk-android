package com.kasirkita.pos.data.repository

import com.kasirkita.pos.core.datastore.AuthSessionDataStoreTestHelper

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.core.datastore.OperationalContextDataStore
import com.kasirkita.pos.data.api.ShiftApi
import com.kasirkita.pos.data.model.CloseShiftRequest
import com.kasirkita.pos.data.model.OpenShiftRequest
import com.kasirkita.pos.data.model.ShiftResponse
import com.kasirkita.pos.data.model.ShiftSummaryResponse
import com.kasirkita.pos.domain.model.AuthSession
import com.kasirkita.pos.domain.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class ShiftRepositoryImplTest {

    private fun repository(
        api: FakeShiftApi = FakeShiftApi(),
        authPreferences: DataStore<Preferences> = InMemoryPreferencesDataStore(),
        session: AuthSession? = null,
    ): ShiftRepositoryImpl {
        val authDataStore = AuthSessionDataStoreTestHelper.createTestStore(authPreferences)
        session?.let { runBlocking { authDataStore.saveSession(it) } }
        val opDataStore = OperationalContextDataStore(InMemoryPreferencesDataStore())
        return ShiftRepositoryImpl(api, authDataStore, opDataStore)
    }

    private fun repositoryWithSession(
        api: FakeShiftApi = FakeShiftApi(),
        authPreferences: DataStore<Preferences> = InMemoryPreferencesDataStore(),
    ): ShiftRepositoryImpl = repository(
        api = api,
        authPreferences = authPreferences,
        session = session(),
    )

    @Test
    fun getCurrentShift_mapsSuccessfulResponseAndPublishesCurrentShift() = runBlocking {
        val api = FakeShiftApi(currentResponse = Response.success(openShiftResponse()))
        val repository = repositoryWithSession(api)

        val shift = repository.getCurrentShift().getOrThrow()

        assertEquals(SHIFT_ID, shift?.id)
        assertEquals(OUTLET_ID, shift?.outletId)
        assertNull(shift?.openingCash)
        assertEquals(shift, repository.currentShift.value)
    }

    @Test
    fun getCurrentShift_whenBackendReturns404_returnsNullWithoutFailure() = runBlocking {
        val api = FakeShiftApi(
            currentResponse = Response.error(
                404,
                "{}".toResponseBody("application/json".toMediaType()),
            ),
        )
        val repository = repositoryWithSession(api)

        val result = repository.getCurrentShift()

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
        assertNull(repository.currentShift.value)
    }

    @Test
    fun openShift_sendsRequestAndPublishesOpenedShift() = runBlocking {
        val api = FakeShiftApi(openResponse = Response.success(openShiftResponse()))
        val repository = repositoryWithSession(api)

        val shift = repository.openShift(OUTLET_ID).getOrThrow()

        assertEquals(
            OpenShiftRequest(OUTLET_ID),
            api.lastOpenRequest,
        )
        assertEquals("OPEN", shift.status)
        assertEquals(shift, repository.currentShift.value)
    }

    @Test
    fun closeShift_returnsShiftSnapshotAndClearsCurrentShift() = runBlocking {
        val api = FakeShiftApi(
            currentResponse = Response.success(openShiftResponse()),
            closeResponse = Response.success(closedShiftResponse()),
        )
        val repository = repositoryWithSession(api)
        repository.getCurrentShift().getOrThrow()

        val shift = repository.closeShift(SHIFT_ID).getOrThrow()

        assertEquals(SHIFT_ID, api.lastClosedShiftId)
        assertEquals(CloseShiftRequest(), api.lastCloseRequest)
        assertEquals("CLOSED", shift.status)
        assertNull(repository.currentShift.value)
    }

    @Test
    fun getCurrentShift_whenTransportFails_returnsSameFailure() = runBlocking {
        val expected = IOException("network unavailable")
        val repository = repositoryWithSession(FakeShiftApi(currentFailure = expected))

        val result = repository.getCurrentShift()

        assertTrue(result.isFailure)
        assertSame(expected, result.exceptionOrNull())
    }

    @Test
    fun clearCurrentShift_removesPublishedShiftWithoutApiCall() = runBlocking {
        val api = FakeShiftApi(currentResponse = Response.success(openShiftResponse()))
        val repository = repositoryWithSession(api)
        repository.getCurrentShift().getOrThrow()

        repository.clearCurrentShift()

        assertNull(repository.currentShift.value)
    }

    @Test
    fun restoreCurrentShift_restoresOpenShiftFromLocalStore() = runBlocking {
        val authPreferences = InMemoryPreferencesDataStore()
        val opPreferences = InMemoryPreferencesDataStore()
        val authDataStore = AuthSessionDataStoreTestHelper.createTestStore(authPreferences)
        val opDataStore = OperationalContextDataStore(opPreferences)

        authDataStore.saveSession(session())
        val api = FakeShiftApi(currentResponse = Response.success(openShiftResponse()))
        val first = ShiftRepositoryImpl(api, authDataStore, opDataStore)
        first.getCurrentShift().getOrThrow()

        val second = ShiftRepositoryImpl(
            FakeShiftApi(currentFailure = IOException("offline")),
            authDataStore,
            opDataStore,
        )
        second.restoreCurrentShift(expectedOutletId = OUTLET_ID)

        val restored = second.currentShift.value
        assertEquals(SHIFT_ID, restored?.id)
        assertEquals(OUTLET_ID, restored?.outletId)
        assertEquals("OPEN", restored?.status)
        assertNull(restored?.openingCash)
    }

    @Test
    fun restoreCurrentShift_whenOutletIdMismatches_doesNotExposeShiftAndClearsCachedShift() = runBlocking {
        val authPreferences = InMemoryPreferencesDataStore()
        val opPreferences = InMemoryPreferencesDataStore()
        val authDataStore = AuthSessionDataStoreTestHelper.createTestStore(authPreferences)
        val opDataStore = OperationalContextDataStore(opPreferences)

        authDataStore.saveSession(session())
        val api = FakeShiftApi(currentResponse = Response.success(openShiftResponse()))
        val first = ShiftRepositoryImpl(api, authDataStore, opDataStore)
        first.getCurrentShift().getOrThrow()

        val second = ShiftRepositoryImpl(
            FakeShiftApi(currentFailure = IOException("offline")),
            authDataStore,
            opDataStore,
        )
        second.restoreCurrentShift(expectedOutletId = "different-outlet-id")

        assertNull(second.currentShift.value)
        assertNull(opDataStore.getShift("tenant-id", "user-id"))
    }

    @Test
    fun restoreCurrentShift_whenNoOutletSelected_doesNotExposeShiftAndClearsCachedShift() = runBlocking {
        val authPreferences = InMemoryPreferencesDataStore()
        val opPreferences = InMemoryPreferencesDataStore()
        val authDataStore = AuthSessionDataStoreTestHelper.createTestStore(authPreferences)
        val opDataStore = OperationalContextDataStore(opPreferences)

        authDataStore.saveSession(session())
        val api = FakeShiftApi(currentResponse = Response.success(openShiftResponse()))
        val first = ShiftRepositoryImpl(api, authDataStore, opDataStore)
        first.getCurrentShift().getOrThrow()

        val second = ShiftRepositoryImpl(
            FakeShiftApi(currentFailure = IOException("offline")),
            authDataStore,
            opDataStore,
        )
        second.restoreCurrentShift(expectedOutletId = null)

        assertNull(second.currentShift.value)
        assertNull(opDataStore.getShift("tenant-id", "user-id"))
    }

    @Test
    fun clearCurrentShift_withExplicitIdentity_clearsStoreEvenWhenAuthSessionNull() = runBlocking {
        val authPreferences = InMemoryPreferencesDataStore()
        val opPreferences = InMemoryPreferencesDataStore()
        val authDataStore = AuthSessionDataStoreTestHelper.createTestStore(authPreferences)
        val opDataStore = OperationalContextDataStore(opPreferences)

        authDataStore.saveSession(session())
        val repository = ShiftRepositoryImpl(
            FakeShiftApi(currentResponse = Response.success(openShiftResponse())),
            authDataStore,
            opDataStore,
        )
        repository.getCurrentShift().getOrThrow()

        authDataStore.clearSession()
        assertNull(authDataStore.getSession())

        repository.clearCurrentShift(tenantId = "tenant-id", userId = "user-id")

        assertNull(opDataStore.getShift("tenant-id", "user-id"))
    }

    @Test
    fun restoreCurrentShift_doesNotRestoreClosedShift() = runBlocking {
        val authPreferences = InMemoryPreferencesDataStore()
        val opPreferences = InMemoryPreferencesDataStore()
        val authDataStore = AuthSessionDataStoreTestHelper.createTestStore(authPreferences)
        val opDataStore = OperationalContextDataStore(opPreferences)

        authDataStore.saveSession(session())
        val api = FakeShiftApi(
            currentResponse = Response.success(openShiftResponse()),
            closeResponse = Response.success(closedShiftResponse()),
        )
        val first = ShiftRepositoryImpl(api, authDataStore, opDataStore)
        first.getCurrentShift().getOrThrow()
        first.closeShift(SHIFT_ID).getOrThrow()

        val second = ShiftRepositoryImpl(
            FakeShiftApi(currentFailure = IOException("offline")),
            authDataStore,
            opDataStore,
        )
        second.restoreCurrentShift()

        assertNull(second.currentShift.value)
    }

    @Test
    fun restoreCurrentShift_doesNotRestoreForDifferentTenant() = runBlocking {
        val authPreferences = InMemoryPreferencesDataStore()
        val authDataStore = AuthSessionDataStoreTestHelper.createTestStore(authPreferences)

        val api = FakeShiftApi(currentResponse = Response.success(openShiftResponse()))
        val first = ShiftRepositoryImpl(
            api,
            authDataStore,
            OperationalContextDataStore(InMemoryPreferencesDataStore()),
        )
        runBlocking { authDataStore.saveSession(session()) }
        first.getCurrentShift().getOrThrow()

        authDataStore.clearSession()
        authDataStore.saveSession(session().copy(tenantId = "other-tenant", userId = "other-user"))

        val opStore = OperationalContextDataStore(InMemoryPreferencesDataStore())
        val second = ShiftRepositoryImpl(
            FakeShiftApi(currentFailure = IOException("offline")),
            authDataStore,
            opStore,
        )
        second.restoreCurrentShift()

        assertNull(second.currentShift.value)
    }

    @Test
    fun openShift_persistsCompletelyBeforeReturning_simulatesProcessDeath() = runBlocking {
        val authPreferences = InMemoryPreferencesDataStore()
        val opPreferences = InMemoryPreferencesDataStore()
        val authDataStore = AuthSessionDataStoreTestHelper.createTestStore(authPreferences)
        val opDataStore = OperationalContextDataStore(opPreferences)

        authDataStore.saveSession(session())
        val api = FakeShiftApi(openResponse = Response.success(openShiftResponse()))
        val repository = ShiftRepositoryImpl(api, authDataStore, opDataStore)

        repository.openShift(OUTLET_ID).getOrThrow()

        val newAuthDataStore = AuthSessionDataStoreTestHelper.createTestStore(authPreferences)
        val newOpDataStore = OperationalContextDataStore(opPreferences)
        val secondRepository = ShiftRepositoryImpl(
            FakeShiftApi(currentFailure = IOException("offline")),
            newAuthDataStore,
            newOpDataStore,
        )

        secondRepository.restoreCurrentShift(expectedOutletId = OUTLET_ID)

        val restored = secondRepository.currentShift.value
        assertEquals(SHIFT_ID, restored?.id)
        assertEquals(OUTLET_ID, restored?.outletId)
        assertEquals("OPEN", restored?.status)
    }

    @Test
    fun getShiftSummary_mapsNestedPartiesTotalsAndProducts() = runBlocking {
        val response = ShiftSummaryResponse(
            shiftId = SHIFT_ID,
            status = "CLOSED",
            outlet = com.kasirkita.pos.data.model.ShiftSummaryPartyResponse(OUTLET_ID, "Outlet A"),
            cashier = com.kasirkita.pos.data.model.ShiftSummaryPartyResponse("user-id", "Kasir A"),
            openedAt = "2026-09-24T08:00:00.000Z",
            closedAt = "2026-09-24T16:00:00.000Z",
            generatedAt = "2026-09-24T16:01:00.000Z",
            transactionCount = 2,
            totals = com.kasirkita.pos.data.model.ShiftSummaryTotalsResponse(30_000L, 10_000L, 20_000L),
            products = listOf(com.kasirkita.pos.data.model.ShiftSummaryProductResponse("product-id", "Kopi", 3)),
        )
        val api = FakeShiftApi(summaryResponse = Response.success(response))

        val summary = repositoryWithSession(api).getShiftSummary(SHIFT_ID).getOrThrow()

        assertEquals(SHIFT_ID, api.lastSummaryShiftId)
        assertEquals("Outlet A", summary.outlet.name)
        assertEquals("user-id", summary.cashier.id)
        assertEquals("2026-09-24T16:01:00.000Z", summary.generatedAt)
        assertEquals(2, summary.transactionCount)
        assertEquals(30_000L, summary.totals.sales)
        assertEquals(10_000L, summary.totals.cash)
        assertEquals(20_000L, summary.totals.qris)
        assertEquals("Kopi", summary.products.single().productName)
        assertEquals(3, summary.products.single().quantity)
    }

    private class FakeShiftApi(
        private val currentResponse: Response<ShiftResponse> = Response.success(openShiftResponse()),
        private val openResponse: Response<ShiftResponse> = Response.success(openShiftResponse()),
        private val closeResponse: Response<ShiftResponse> = Response.success(closedShiftResponse()),
        private val summaryResponse: Response<ShiftSummaryResponse>? = null,
        private val currentFailure: Throwable? = null,
    ) : ShiftApi {
        var lastOpenRequest: OpenShiftRequest? = null
        var lastClosedShiftId: String? = null
        var lastCloseRequest: CloseShiftRequest? = null
        var lastSummaryShiftId: String? = null

        override suspend fun getCurrentShift(): Response<ShiftResponse> {
            currentFailure?.let { throw it }
            return currentResponse
        }

        override suspend fun openShift(request: OpenShiftRequest): Response<ShiftResponse> {
            lastOpenRequest = request
            return openResponse
        }

        override suspend fun getShiftSummary(shiftId: String): Response<ShiftSummaryResponse> {
            lastSummaryShiftId = shiftId
            return summaryResponse ?: Response.success(
                com.kasirkita.pos.data.model.ShiftSummaryResponse(
                    shiftId = shiftId,
                    status = "CLOSED",
                    outlet = com.kasirkita.pos.data.model.ShiftSummaryPartyResponse("outlet-id", "Outlet"),
                    cashier = com.kasirkita.pos.data.model.ShiftSummaryPartyResponse("user-id", "Kasir"),
                    openedAt = "2026-09-24T08:00:00.000Z",
                    closedAt = "2026-09-24T16:00:00.000Z",
                    generatedAt = "2026-09-24T16:01:00.000Z",
                    transactionCount = 0,
                    totals = com.kasirkita.pos.data.model.ShiftSummaryTotalsResponse(0L, 0L, 0L),
                    products = emptyList(),
                )
            )
        }

        override suspend fun closeShift(
            shiftId: String,
            request: CloseShiftRequest,
        ): Response<ShiftResponse> {
            lastClosedShiftId = shiftId
            lastCloseRequest = request
            return closeResponse
        }
    }

    private class InMemoryPreferencesDataStore(
        initial: Preferences = emptyPreferences(),
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    private companion object {
        const val SHIFT_ID = "shift-id"
        const val OUTLET_ID = "outlet-id"

        fun session() = AuthSession(
            userId = "user-id",
            userName = "Kasir Utama",
            tenantId = "tenant-id",
            role = UserRole.CASHIER,
            outletId = OUTLET_ID,
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresAt = Long.MAX_VALUE,
            deviceId = "device-id",
        )

        fun openShiftResponse() = ShiftResponse(
            shiftId = SHIFT_ID,
            outletId = OUTLET_ID,
            userId = "user-id",
            openingCash = null,
            closingCash = null,
            expectedCash = null,
            difference = null,
            status = "OPEN",
            openedAt = "2026-09-24T08:00:00.000Z",
            closedAt = null,
        )

        fun closedShiftResponse() = openShiftResponse().copy(
            status = "CLOSED",
            closedAt = "2026-09-24T16:00:00.000Z",
        )
    }
}
