package com.kasirkita.pos.core.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.core.datastore.DeviceIdProvider
import com.kasirkita.pos.data.api.AuthV2Api
import com.kasirkita.pos.data.model.AuthTokenResponse
import com.kasirkita.pos.data.model.CurrentUserResponse
import com.kasirkita.pos.data.model.LogoutRequest
import com.kasirkita.pos.data.model.PinLoginRequest
import com.kasirkita.pos.data.model.RefreshTokenRequest
import com.kasirkita.pos.data.model.StoreResolveRequest
import com.kasirkita.pos.data.model.StoreResolveResponse
import com.kasirkita.pos.domain.model.AuthSession
import com.kasirkita.pos.domain.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class RefreshTokenCoordinatorTest {

    @Test
    fun refreshSuccess_returnsNewAccessTokenAndUsesStoredRefreshToken() = runBlocking {
        val store = sessionStore()
        val api = FakeAuthV2Api()
        val coordinator = coordinator(api, store)

        val result = coordinator.refreshAccessToken(OLD_ACCESS_TOKEN)

        assertEquals(NEW_ACCESS_TOKEN, result)
        assertEquals(1, api.refreshCalls.get())
        assertEquals(OLD_REFRESH_TOKEN, api.lastRefreshRequest?.refreshToken)
    }

    @Test
    fun refreshFailure_clearsSessionButKeepsDeviceId() = runBlocking {
        val store = sessionStore()
        val api = FakeAuthV2Api(
            refresh = { throw IOException("network unavailable") },
        )
        val coordinator = coordinator(api, store)

        val result = coordinator.refreshAccessToken(OLD_ACCESS_TOKEN)

        assertNull(result)
        assertNull(store.getSession())
        assertEquals(DEVICE_ID, DeviceIdProvider(store).getDeviceId())
    }

    @Test
    fun parallelRequests_withSameFailedToken_performSingleRefresh() = runBlocking {
        val store = sessionStore()
        val api = FakeAuthV2Api(
            refresh = {
                delay(50L)
                tokenResponse()
            },
        )
        val coordinator = coordinator(api, store)

        val results = List(8) {
            async(Dispatchers.Default) {
                coordinator.refreshAccessToken(OLD_ACCESS_TOKEN)
            }
        }.awaitAll()

        assertTrue(results.all { token -> token == NEW_ACCESS_TOKEN })
        assertEquals(1, api.refreshCalls.get())
    }

    @Test
    fun refreshSuccess_updatesRotatedTokenPairAndExpiryAtomically() = runBlocking {
        val store = sessionStore()
        val original = requireNotNull(store.getSession())
        val coordinator = coordinator(FakeAuthV2Api(), store)

        coordinator.refreshAccessToken(OLD_ACCESS_TOKEN)

        val updated = requireNotNull(store.getSession())
        assertEquals(original.userId, updated.userId)
        assertEquals(original.userName, updated.userName)
        assertEquals(original.tenantId, updated.tenantId)
        assertEquals(original.role, updated.role)
        assertEquals(original.outletId, updated.outletId)
        assertEquals(original.deviceId, updated.deviceId)
        assertEquals(NEW_ACCESS_TOKEN, updated.accessToken)
        assertEquals(NEW_REFRESH_TOKEN, updated.refreshToken)
        assertEquals(NOW_MILLIS + EXPIRES_IN_SECONDS * 1_000L, updated.expiresAt)
    }

    @Test
    fun delayedRefresh_doesNotOverwriteAReplacementAccount() = runBlocking {
        val store = sessionStore()
        val refreshStarted = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        val api = FakeAuthV2Api(
            refresh = {
                refreshStarted.complete(Unit)
                releaseRefresh.await()
                tokenResponse()
            },
        )
        val coordinator = coordinator(api, store)

        val refresh = async(Dispatchers.Default) {
            coordinator.refreshAccessToken(OLD_ACCESS_TOKEN)
        }
        refreshStarted.await()
        store.clearSession()
        val replacement = AuthSession(
            userId = "replacement-user",
            userName = "Kasir Pengganti",
            tenantId = "replacement-tenant",
            role = UserRole.CASHIER,
            outletId = "replacement-outlet",
            accessToken = "replacement-access-token",
            refreshToken = "replacement-refresh-token",
            expiresAt = Long.MAX_VALUE,
            deviceId = DEVICE_ID,
        )
        store.saveSession(replacement)
        releaseRefresh.complete(Unit)

        assertNull(refresh.await())
        assertEquals(replacement, store.getSession())
    }

    private suspend fun sessionStore(): AuthSessionDataStore = AuthSessionDataStore(
        InMemoryPreferencesDataStore(),
    ).also { store ->
        store.saveSession(
            AuthSession(
                userId = "user-id",
                userName = "Kasir Utama",
                tenantId = "tenant-id",
                role = UserRole.CASHIER,
                outletId = "outlet-id",
                accessToken = OLD_ACCESS_TOKEN,
                refreshToken = OLD_REFRESH_TOKEN,
                expiresAt = NOW_MILLIS - 1L,
                deviceId = DEVICE_ID,
            ),
        )
    }

    private fun coordinator(
        api: AuthV2Api,
        store: AuthSessionDataStore,
    ) = RefreshTokenCoordinator(
        authV2Api = api,
        authSessionDataStore = store,
        currentTimeMillis = { NOW_MILLIS },
    )

    private class FakeAuthV2Api(
        private val refresh: suspend (RefreshTokenRequest) -> AuthTokenResponse = {
            tokenResponse()
        },
    ) : AuthV2Api {
        val refreshCalls = AtomicInteger(0)
        var lastRefreshRequest: RefreshTokenRequest? = null

        override suspend fun resolveStore(request: StoreResolveRequest): StoreResolveResponse =
            error("Not used")

        override suspend fun pinLogin(request: PinLoginRequest): AuthTokenResponse =
            error("Not used")

        override suspend fun refreshToken(request: RefreshTokenRequest): AuthTokenResponse {
            refreshCalls.incrementAndGet()
            lastRefreshRequest = request
            return refresh(request)
        }

        override suspend fun logout(request: LogoutRequest) = error("Not used")

        override suspend fun getCurrentUser(): CurrentUserResponse = error("Not used")
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
        const val OLD_ACCESS_TOKEN = "old-access-token"
        const val OLD_REFRESH_TOKEN = "old-refresh-token"
        const val NEW_ACCESS_TOKEN = "new-access-token"
        const val NEW_REFRESH_TOKEN = "new-refresh-token"
        const val DEVICE_ID = "8f1fbf18-ed1e-4db8-a78d-857238e08e62"
        const val NOW_MILLIS = 1_000_000L
        const val EXPIRES_IN_SECONDS = 900L

        fun tokenResponse() = AuthTokenResponse(
            accessToken = NEW_ACCESS_TOKEN,
            refreshToken = NEW_REFRESH_TOKEN,
            expiresIn = EXPIRES_IN_SECONDS,
        )
    }
}
