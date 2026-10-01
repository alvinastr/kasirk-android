package com.kasirkita.pos.core.network
import com.kasirkita.pos.core.datastore.AuthSessionDataStoreTestHelper

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.kasirkita.pos.core.datastore.AuthSessionDataStore
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthAuthenticatorTest {

    @Test
    fun authenticate_retriesRequestWithRefreshedBearerToken() = runBlocking {
        val api = FakeAuthV2Api()
        val authenticator = authenticator(api)
        val response = unauthorizedResponse("/products", OLD_ACCESS_TOKEN)

        val retry = authenticator.authenticate(null, response)

        assertEquals("Bearer $NEW_ACCESS_TOKEN", retry?.header("Authorization"))
        assertEquals(1, api.refreshCalls)
    }

    @Test
    fun authEndpoints_doNotTriggerRefresh() = runBlocking {
        val excludedPaths = listOf(
            "/auth/v2/refresh",
            "/auth/v2/pin/login",
            "/auth/v2/logout",
        )

        excludedPaths.forEach { path ->
            val api = FakeAuthV2Api()
            val authenticator = authenticator(api)

            assertNull(authenticator.authenticate(null, unauthorizedResponse(path, OLD_ACCESS_TOKEN)))
            assertEquals(0, api.refreshCalls)
        }
    }

    private suspend fun authenticator(api: AuthV2Api): AuthAuthenticator {
        val store = AuthSessionDataStoreTestHelper.createTestStore()
        store.saveSession(
            AuthSession(
                userId = "user-id",
                userName = "Kasir Utama",
                tenantId = "tenant-id",
                role = UserRole.CASHIER,
                outletId = "outlet-id",
                accessToken = OLD_ACCESS_TOKEN,
                refreshToken = "old-refresh-token",
                expiresAt = 0L,
                deviceId = "8f1fbf18-ed1e-4db8-a78d-857238e08e62",
            ),
        )
        return AuthAuthenticator(
            RefreshTokenCoordinator(
                authV2Api = api,
                authSessionDataStore = store,
                currentTimeMillis = { 1_000L },
            ),
        )
    }

    private fun unauthorizedResponse(path: String, accessToken: String): Response {
        val request = Request.Builder()
            .url("http://localhost$path")
            .header("Authorization", "Bearer $accessToken")
            .build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()
    }

    private class FakeAuthV2Api : AuthV2Api {
        var refreshCalls = 0

        override suspend fun resolveStore(request: StoreResolveRequest): StoreResolveResponse =
            error("Not used")

        override suspend fun pinLogin(request: PinLoginRequest): AuthTokenResponse =
            error("Not used")

        override suspend fun refreshToken(request: RefreshTokenRequest): AuthTokenResponse {
            refreshCalls += 1
            return AuthTokenResponse(
                accessToken = NEW_ACCESS_TOKEN,
                refreshToken = "new-refresh-token",
                expiresIn = 900L,
            )
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
        const val NEW_ACCESS_TOKEN = "new-access-token"
    }
}
