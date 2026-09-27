package com.kasirkita.pos.presentation.authv2

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.core.datastore.DeviceIdProvider
import com.kasirkita.pos.domain.model.AuthTokens
import com.kasirkita.pos.domain.model.CurrentUser
import com.kasirkita.pos.domain.model.ResolvedStore
import com.kasirkita.pos.domain.model.StoreTenant
import com.kasirkita.pos.domain.model.StoreUser
import com.kasirkita.pos.domain.model.UserRole
import com.kasirkita.pos.domain.repository.AuthV2Repository
import com.kasirkita.pos.domain.usecase.PinLoginUseCase
import com.kasirkita.pos.domain.usecase.ResolveStoreUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AuthV2ViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun resolveStore_success_transitionsFromLoadingToUserSelection() {
        val repository = FakeAuthV2Repository()
        val viewModel = viewModel(repository)

        viewModel.resolveStore(" toko-01 ")

        assertTrue((viewModel.state.value as AuthV2State.StoreLogin).isLoading)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value as AuthV2State.UserSelection
        assertEquals("TOKO-01", repository.lastStoreCode)
        assertEquals("tenant-id", state.store.tenant.id)
        assertEquals("Kasir Utama", state.store.users.single().name)
    }

    @Test
    fun resolveStore_failure_returnsReadableStoreState() {
        val repository = FakeAuthV2Repository(
            resolveResult = Result.failure(
                httpException(404, "STORE_NOT_FOUND"),
            ),
        )
        val viewModel = viewModel(repository)

        viewModel.resolveStore("TOKO-01")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value as AuthV2State.StoreLogin
        assertFalse(state.isLoading)
        assertEquals(
            "Kode toko tidak ditemukan. Periksa kode lalu coba lagi.",
            state.errorMessage,
        )
    }

    @Test
    fun pinLogin_success_savesSessionAndTransitionsToAuthenticated() = runBlocking {
        val repository = FakeAuthV2Repository()
        val dataStore = AuthSessionDataStore(InMemoryPreferencesDataStore())
        val viewModel = viewModel(repository, dataStore)

        viewModel.resolveStore("TOKO-01")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.selectUser(store().users.single())
        viewModel.loginWithPin("123456", "Kasir Depan")

        assertTrue((viewModel.state.value as AuthV2State.PinLogin).isLoading)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value as AuthV2State.Authenticated
        val stored = requireNotNull(dataStore.getSession())
        assertEquals("tenant-id", repository.lastTenantId)
        assertEquals("user-id", repository.lastUserId)
        assertEquals("123456", repository.lastPin)
        assertEquals("Kasir Depan", repository.lastDeviceName)
        assertEquals(repository.lastDeviceId, state.session.deviceId)
        assertEquals(state.session, stored)
        assertEquals("access-token", stored.accessToken)
        assertEquals("refresh-token", stored.refreshToken)
    }

    @Test
    fun pinLogin_failure_staysOnPinScreenWithReadableError() {
        val repository = FakeAuthV2Repository(
            pinResult = Result.failure(
                httpException(401, "INVALID_CREDENTIALS"),
            ),
        )
        val viewModel = viewModel(repository)

        viewModel.resolveStore("TOKO-01")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.selectUser(store().users.single())
        viewModel.loginWithPin("123456", "Kasir Depan")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value as AuthV2State.PinLogin
        assertFalse(state.isLoading)
        assertEquals("PIN salah atau pengguna tidak dapat masuk.", state.errorMessage)
    }

    private fun viewModel(
        repository: AuthV2Repository,
        dataStore: AuthSessionDataStore = AuthSessionDataStore(
            InMemoryPreferencesDataStore(),
        ),
    ) = AuthV2ViewModel(
        resolveStoreUseCase = ResolveStoreUseCase(repository),
        pinLoginUseCase = PinLoginUseCase(repository),
        authSessionDataStore = dataStore,
        deviceIdProvider = DeviceIdProvider(dataStore),
    )

    private class FakeAuthV2Repository(
        private val resolveResult: Result<ResolvedStore> = Result.success(store()),
        private val pinResult: Result<AuthTokens> = Result.success(
            AuthTokens(
                accessToken = "access-token",
                refreshToken = "refresh-token",
                expiresInSeconds = 900L,
            ),
        ),
    ) : AuthV2Repository {
        var lastStoreCode: String? = null
        var lastTenantId: String? = null
        var lastUserId: String? = null
        var lastPin: String? = null
        var lastDeviceId: String? = null
        var lastDeviceName: String? = null

        override suspend fun resolveStore(storeCode: String): Result<ResolvedStore> {
            lastStoreCode = storeCode
            return resolveResult
        }

        override suspend fun pinLogin(
            tenantId: String,
            userId: String,
            pin: String,
            deviceId: String,
            deviceName: String?,
        ): Result<AuthTokens> {
            lastTenantId = tenantId
            lastUserId = userId
            lastPin = pin
            lastDeviceId = deviceId
            lastDeviceName = deviceName
            return pinResult
        }

        override suspend fun refreshToken(refreshToken: String): Result<AuthTokens> =
            error("Not used")

        override suspend fun getCurrentUser(): Result<CurrentUser> = error("Not used")

        override suspend fun logout(refreshToken: String): Result<Unit> = error("Not used")
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
        fun store() = ResolvedStore(
            tenant = StoreTenant(
                id = "tenant-id",
                name = "Toko Kita",
            ),
            users = listOf(
                StoreUser(
                    id = "user-id",
                    name = "Kasir Utama",
                    role = UserRole.CASHIER,
                    outletId = "outlet-id",
                ),
            ),
        )

        fun httpException(code: Int, errorCode: String): HttpException = HttpException(
            Response.error<Any>(
                code,
                "{\"error_code\":\"$errorCode\"}"
                    .toResponseBody("application/json".toMediaType()),
            ),
        )
    }
}
