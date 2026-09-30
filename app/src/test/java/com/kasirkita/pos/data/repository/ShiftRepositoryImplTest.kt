package com.kasirkita.pos.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.core.datastore.OperationalContextDataStore
import com.kasirkita.pos.data.api.ShiftApi
import com.kasirkita.pos.data.model.CloseShiftRequest
import com.kasirkita.pos.data.model.OpenShiftRequest
import com.kasirkita.pos.data.model.ShiftResponse
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
        val authDataStore = AuthSessionDataStore(authPreferences)
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
        assertEquals(50_000L, shift?.openingCash)
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

        val shift = repository.openShift(OUTLET_ID, 50_000L).getOrThrow()

        assertEquals(
            OpenShiftRequest(OUTLET_ID, 50_000L),
            api.lastOpenRequest,
        )
        assertEquals("OPEN", shift.status)
        assertEquals(shift, repository.currentShift.value)
    }

    @Test
    fun closeShift_returnsReconciliationAndClearsCurrentShift() = runBlocking {
        val api = FakeShiftApi(
            currentResponse = Response.success(openShiftResponse()),
            closeResponse = Response.success(closedShiftResponse()),
        )
        val repository = repositoryWithSession(api)
        repository.getCurrentShift().getOrThrow()

        val shift = repository.closeShift(SHIFT_ID, 200_000L).getOrThrow()

        assertEquals(SHIFT_ID, api.lastClosedShiftId)
        assertEquals(CloseShiftRequest(200_000L), api.lastCloseRequest)
        assertEquals(210_000L, shift.expectedCash)
        assertEquals(-10_000L, shift.difference)
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
        val authDataStore = AuthSessionDataStore(authPreferences)
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
        second.restoreCurrentShift()

        val restored = second.currentShift.value
        assertEquals(SHIFT_ID, restored?.id)
        assertEquals(OUTLET_ID, restored?.outletId)
        assertEquals("OPEN", restored?.status)
        assertEquals(50_000L, restored?.openingCash)
    }

    @Test
    fun restoreCurrentShift_doesNotRestoreClosedShift() = runBlocking {
        val authPreferences = InMemoryPreferencesDataStore()
        val opPreferences = InMemoryPreferencesDataStore()
        val authDataStore = AuthSessionDataStore(authPreferences)
        val opDataStore = OperationalContextDataStore(opPreferences)

        authDataStore.saveSession(session())
        val api = FakeShiftApi(
            currentResponse = Response.success(openShiftResponse()),
            closeResponse = Response.success(closedShiftResponse()),
        )
        val first = ShiftRepositoryImpl(api, authDataStore, opDataStore)
        first.getCurrentShift().getOrThrow()
        first.closeShift(SHIFT_ID, 200_000L).getOrThrow()

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
        val authDataStore = AuthSessionDataStore(authPreferences)

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

    private class FakeShiftApi(
        private val currentResponse: Response<ShiftResponse> = Response.success(openShiftResponse()),
        private val openResponse: Response<ShiftResponse> = Response.success(openShiftResponse()),
        private val closeResponse: Response<ShiftResponse> = Response.success(closedShiftResponse()),
        private val currentFailure: Throwable? = null,
    ) : ShiftApi {
        var lastOpenRequest: OpenShiftRequest? = null
        var lastClosedShiftId: String? = null
        var lastCloseRequest: CloseShiftRequest? = null

        override suspend fun getCurrentShift(): Response<ShiftResponse> {
            currentFailure?.let { throw it }
            return currentResponse
        }

        override suspend fun openShift(request: OpenShiftRequest): Response<ShiftResponse> {
            lastOpenRequest = request
            return openResponse
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
            openingCash = 50_000L,
            closingCash = null,
            expectedCash = null,
            difference = null,
            status = "OPEN",
            openedAt = "2026-09-24T08:00:00.000Z",
            closedAt = null,
        )

        fun closedShiftResponse() = openShiftResponse().copy(
            closingCash = 200_000L,
            expectedCash = 210_000L,
            difference = -10_000L,
            status = "CLOSED",
            closedAt = "2026-09-24T16:00:00.000Z",
        )
    }
}
