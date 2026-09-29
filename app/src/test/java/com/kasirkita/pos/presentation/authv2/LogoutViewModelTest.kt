package com.kasirkita.pos.presentation.authv2

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.core.session.SessionBoundaryCleaner
import com.kasirkita.pos.data.repository.CartRepositoryImpl
import com.kasirkita.pos.domain.model.AuthSession
import com.kasirkita.pos.domain.model.AuthTokens
import com.kasirkita.pos.domain.model.CurrentUser
import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.model.ResolvedStore
import com.kasirkita.pos.domain.model.Shift
import com.kasirkita.pos.domain.model.UserRole
import com.kasirkita.pos.domain.repository.AuthV2Repository
import com.kasirkita.pos.domain.repository.OutletRepository
import com.kasirkita.pos.domain.repository.ShiftRepository
import com.kasirkita.pos.domain.usecase.LogoutUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LogoutViewModelTest {

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
    fun logoutSuccess_callsBackendAndClearsInMemoryState() = runBlocking {
        val fixture = fixture()

        fixture.viewModel.logout()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("refresh-token", fixture.authRepository.lastLogoutToken)
        assertNull(fixture.outletRepository.selectedOutlet.value)
        assertTrue(fixture.cartRepository.getCart().value.items.isEmpty())
        assertNull(fixture.shiftRepository.currentShift.value)
        assertEquals(LogoutState.LoggedOut, fixture.viewModel.state.value)
    }

    @Test
    fun logoutNetworkFailure_stillClearsAllLocalState() = runBlocking {
        val fixture = fixture(
            logoutResult = Result.failure(IOException("network unavailable")),
        )

        fixture.viewModel.logout()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(fixture.authSessionDataStore.getSession())
        assertNull(fixture.outletRepository.selectedOutlet.value)
        assertTrue(fixture.cartRepository.getCart().value.items.isEmpty())
        assertNull(fixture.shiftRepository.currentShift.value)
        assertEquals(LogoutState.LoggedOut, fixture.viewModel.state.value)
    }

    @Test
    fun logout_removesPersistedAuthV2Session() = runBlocking {
        val fixture = fixture()

        fixture.viewModel.logout()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(fixture.authSessionDataStore.getSession())
    }

    @Test
    fun localCleanupFailure_stillClearsRemainingStateAndAuthLast() = runBlocking {
        val fixture = fixture(
            outletClearFailure = IllegalStateException("outlet clear failed"),
        )

        fixture.viewModel.logout()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(fixture.authSessionDataStore.getSession())
        assertTrue(fixture.cartRepository.getCart().value.items.isEmpty())
        assertNull(fixture.shiftRepository.currentShift.value)
        assertEquals(
            LogoutState.Error("Logout lokal belum selesai. Coba lagi."),
            fixture.viewModel.state.value,
        )
    }

    private fun fixture(
        logoutResult: Result<Unit> = Result.success(Unit),
        outletClearFailure: Throwable? = null,
    ): Fixture {
        val authSessionDataStore = AuthSessionDataStore(InMemoryPreferencesDataStore())
        runBlocking {
            authSessionDataStore.saveSession(authSession())
        }
        val authRepository = FakeAuthV2Repository(logoutResult)
        val outletRepository = FakeOutletRepository(outletClearFailure)
        val cartRepository = CartRepositoryImpl().apply {
            addProduct(product())
        }
        val shiftRepository = FakeShiftRepository()
        val viewModel = LogoutViewModel(
            logoutUseCase = LogoutUseCase(authRepository),
            authSessionDataStore = authSessionDataStore,
            sessionBoundaryCleaner = SessionBoundaryCleaner(
                cartRepository = cartRepository,
                outletRepository = outletRepository,
                shiftRepository = shiftRepository,
            ),
        )
        return Fixture(
            viewModel = viewModel,
            authSessionDataStore = authSessionDataStore,
            authRepository = authRepository,
            outletRepository = outletRepository,
            cartRepository = cartRepository,
            shiftRepository = shiftRepository,
        )
    }

    private data class Fixture(
        val viewModel: LogoutViewModel,
        val authSessionDataStore: AuthSessionDataStore,
        val authRepository: FakeAuthV2Repository,
        val outletRepository: FakeOutletRepository,
        val cartRepository: CartRepositoryImpl,
        val shiftRepository: FakeShiftRepository,
    )

    private class FakeAuthV2Repository(
        private val logoutResult: Result<Unit>,
    ) : AuthV2Repository {
        var lastLogoutToken: String? = null

        override suspend fun resolveStore(storeCode: String): Result<ResolvedStore> =
            error("Not used")

        override suspend fun pinLogin(
            tenantId: String,
            userId: String,
            pin: String,
            deviceId: String,
            deviceName: String?,
        ): Result<AuthTokens> = error("Not used")

        override suspend fun refreshToken(refreshToken: String): Result<AuthTokens> =
            error("Not used")

        override suspend fun getCurrentUser(): Result<CurrentUser> = error("Not used")

        override suspend fun logout(refreshToken: String): Result<Unit> {
            lastLogoutToken = refreshToken
            return logoutResult
        }
    }

    private class FakeOutletRepository(
        private val clearFailure: Throwable?,
    ) : OutletRepository {
        private val outlet = MutableStateFlow<Outlet?>(outlet())
        override val selectedOutlet: StateFlow<Outlet?> = outlet

        override suspend fun getOutlets(): Result<List<Outlet>> =
            Result.success(listOfNotNull(outlet.value))

        override fun selectOutlet(outlet: Outlet) {
            this.outlet.value = outlet
        }

        override fun clearSelectedOutlet() {
            clearFailure?.let { throwable -> throw throwable }
            outlet.value = null
        }
    }

    private class FakeShiftRepository : ShiftRepository {
        override val currentShift = MutableStateFlow<Shift?>(shift())

        override suspend fun getCurrentShift(): Result<Shift?> = error("Not used")

        override suspend fun openShift(
            outletId: String,
            openingCash: Long,
        ): Result<Shift> = error("Not used")

        override suspend fun closeShift(
            shiftId: String,
            closingCash: Long,
        ): Result<Shift> = error("Not used")

        override fun clearCurrentShift() {
            currentShift.value = null
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
        fun authSession() = AuthSession(
            userId = "user-id",
            userName = "Kasir Utama",
            tenantId = "tenant-id",
            role = UserRole.CASHIER,
            outletId = "outlet-id",
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresAt = Long.MAX_VALUE,
            deviceId = "device-id",
        )

        fun outlet() = Outlet(
            id = "outlet-id",
            tenantId = "tenant-id",
            name = "Outlet Utama",
            address = null,
            isActive = true,
            createdAt = "2026-09-27T00:00:00Z",
        )

        fun product() = Product(
            id = "product-id",
            tenantId = "tenant-id",
            categoryId = null,
            name = "Kopi",
            sku = "KOPI-01",
            price = 15_000L,
            cost = 8_000L,
            minimumStock = 0,
            trackStock = false,
            isActive = true,
            createdAt = "2026-09-27T00:00:00Z",
        )

        fun shift() = Shift(
            id = "shift-id",
            outletId = "outlet-id",
            userId = "user-id",
            openingCash = 50_000L,
            closingCash = null,
            expectedCash = null,
            difference = null,
            status = "OPEN",
            openedAt = "2026-09-27T08:00:00Z",
            closedAt = null,
        )
    }
}
