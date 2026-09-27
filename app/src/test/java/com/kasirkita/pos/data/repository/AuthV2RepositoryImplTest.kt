package com.kasirkita.pos.data.repository

import com.kasirkita.pos.data.api.AuthV2Api
import com.kasirkita.pos.data.model.AuthTokenResponse
import com.kasirkita.pos.data.model.CurrentUserResponse
import com.kasirkita.pos.data.model.LogoutRequest
import com.kasirkita.pos.data.model.PinLoginRequest
import com.kasirkita.pos.data.model.RefreshTokenRequest
import com.kasirkita.pos.data.model.StoreResolveRequest
import com.kasirkita.pos.data.model.StoreResolveResponse
import com.kasirkita.pos.data.model.StoreTenantResponse
import com.kasirkita.pos.data.model.StoreUserResponse
import com.kasirkita.pos.domain.model.UserRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AuthV2RepositoryImplTest {

    @Test
    fun resolveStore_mapsResponseAndForwardsStoreCode() = runBlocking {
        val api = FakeAuthV2Api()
        val repository = AuthV2RepositoryImpl(api)

        val store = repository.resolveStore("TOKO-01").getOrThrow()

        assertEquals("TOKO-01", api.resolveRequest?.storeCode)
        assertEquals("tenant-id", store.tenant.id)
        assertEquals("Toko Kita", store.tenant.name)
        assertEquals(UserRole.OWNER, store.users.single().role)
        assertEquals("outlet-id", store.users.single().outletId)
    }

    @Test
    fun pinLogin_mapsTokensAndForwardsDeviceIdentity() = runBlocking {
        val api = FakeAuthV2Api()
        val repository = AuthV2RepositoryImpl(api)

        val tokens = repository.pinLogin(
            tenantId = "tenant-id",
            userId = "user-id",
            pin = "123456",
            deviceId = "device-id",
            deviceName = "Kasir Depan",
        ).getOrThrow()

        assertEquals("tenant-id", api.pinRequest?.tenantId)
        assertEquals("user-id", api.pinRequest?.userId)
        assertEquals("123456", api.pinRequest?.pin)
        assertEquals("device-id", api.pinRequest?.deviceId)
        assertEquals("Kasir Depan", api.pinRequest?.deviceName)
        assertEquals("access-token", tokens.accessToken)
        assertEquals("refresh-token", tokens.refreshToken)
        assertEquals(900L, tokens.expiresInSeconds)
    }

    @Test
    fun refreshCurrentUserAndLogout_mapAndForwardValues() = runBlocking {
        val api = FakeAuthV2Api()
        val repository = AuthV2RepositoryImpl(api)

        val refreshed = repository.refreshToken("old-refresh-token").getOrThrow()
        val currentUser = repository.getCurrentUser().getOrThrow()
        repository.logout("new-refresh-token").getOrThrow()

        assertEquals("old-refresh-token", api.refreshRequest?.refreshToken)
        assertEquals("access-token", refreshed.accessToken)
        assertEquals("user-id", currentUser.id)
        assertEquals("tenant-id", currentUser.tenantId)
        assertEquals(UserRole.ADMIN, currentUser.role)
        assertEquals("new-refresh-token", api.logoutRequest?.refreshToken)
    }

    @Test
    fun apiFailure_isReturnedWithoutReplacement() = runBlocking {
        val expected = IOException("network unavailable")
        val repository = AuthV2RepositoryImpl(FakeAuthV2Api(failure = expected))

        val result = repository.resolveStore("TOKO-01")

        assertTrue(result.isFailure)
        assertSame(expected, result.exceptionOrNull())
    }

    private class FakeAuthV2Api(
        private val failure: Throwable? = null,
    ) : AuthV2Api {
        var resolveRequest: StoreResolveRequest? = null
        var pinRequest: PinLoginRequest? = null
        var refreshRequest: RefreshTokenRequest? = null
        var logoutRequest: LogoutRequest? = null

        override suspend fun resolveStore(request: StoreResolveRequest): StoreResolveResponse {
            resolveRequest = request
            failIfNeeded()
            return StoreResolveResponse(
                tenant = StoreTenantResponse(
                    id = "tenant-id",
                    name = "Toko Kita",
                ),
                users = listOf(
                    StoreUserResponse(
                        id = "user-id",
                        name = "Pemilik Toko",
                        role = "OWNER",
                        outletId = "outlet-id",
                    ),
                ),
            )
        }

        override suspend fun pinLogin(request: PinLoginRequest): AuthTokenResponse {
            pinRequest = request
            failIfNeeded()
            return tokenResponse()
        }

        override suspend fun refreshToken(request: RefreshTokenRequest): AuthTokenResponse {
            refreshRequest = request
            failIfNeeded()
            return tokenResponse()
        }

        override suspend fun logout(request: LogoutRequest) {
            logoutRequest = request
            failIfNeeded()
        }

        override suspend fun getCurrentUser(): CurrentUserResponse {
            failIfNeeded()
            return CurrentUserResponse(
                id = "user-id",
                name = "Admin Toko",
                role = "ADMIN",
                tenantId = "tenant-id",
                outletId = "outlet-id",
            )
        }

        private fun failIfNeeded() {
            failure?.let { throwable -> throw throwable }
        }

        private fun tokenResponse() = AuthTokenResponse(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresIn = 900L,
        )
    }
}
