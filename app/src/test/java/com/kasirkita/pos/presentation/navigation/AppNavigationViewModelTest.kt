package com.kasirkita.pos.presentation.navigation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.core.network.RefreshTokenCoordinator
import com.kasirkita.pos.core.session.SessionBoundaryCleaner
import com.kasirkita.pos.data.api.AuthV2Api
import com.kasirkita.pos.data.repository.CartRepositoryImpl
import com.kasirkita.pos.data.model.AuthTokenResponse
import com.kasirkita.pos.data.model.CurrentUserResponse
import com.kasirkita.pos.data.model.LogoutRequest
import com.kasirkita.pos.data.model.PinLoginRequest
import com.kasirkita.pos.data.model.RefreshTokenRequest
import com.kasirkita.pos.data.model.StoreResolveRequest
import com.kasirkita.pos.data.model.StoreResolveResponse
import com.kasirkita.pos.domain.model.AuthSession
import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.model.Shift
import com.kasirkita.pos.domain.model.UserRole
import com.kasirkita.pos.domain.repository.OutletRepository
import com.kasirkita.pos.domain.repository.ShiftRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AppNavigationViewModelTest {

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
    fun noSession_opensAuthV2Flow() {
        val fixture = fixture()

        fixture.viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(SessionState.Unauthenticated, fixture.state())
        assertEquals(
            AuthV2Screen.Graph.route,
            startupRouteFor(fixture.state()),
        )
    }

    @Test
    fun validLocalSession_startsAtHomeAndRefreshesContextAsynchronously() = runBlocking {
        val fixture = fixture(
            session = authSession(expiresAt = Long.MAX_VALUE),
        )

        fixture.viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = fixture.state() as SessionState.Authenticated
        val navigationSession = state.session as NavigationSession.AuthV2
        assertEquals("user-id", navigationSession.value.userId)
        assertEquals(Screen.Home.route, startupRouteFor(state))
        assertEquals(1, fixture.api.refreshCalls)
        assertEquals(1, fixture.shiftRepository.getCurrentShiftCalls)
        assertEquals("outlet-id", fixture.outletRepository.selectedOutlet.value?.id)
        assertEquals(1, fixture.cartRepository.getCart().value.totalItems())
        assertEquals("shift-id", fixture.shiftRepository.currentShift.value?.id)
    }

    @Test
    fun expiredLocalSession_requiresLoginWithoutNetworkRefresh() = runBlocking {
        val fixture = fixture(
            session = authSession(expiresAt = 0L),
        )

        fixture.viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(SessionState.Unauthenticated, fixture.state())
        assertNull(fixture.authSessionDataStore.getSession())
        assertEquals(0, fixture.api.refreshCalls)
        assertNull(fixture.outletRepository.selectedOutlet.value)
        assertEquals(0, fixture.cartRepository.getCart().value.totalItems())
        assertNull(fixture.shiftRepository.currentShift.value)
        assertEquals(AuthV2Screen.Graph.route, startupRouteFor(fixture.state()))
    }

    @Test
    fun networkValidationFailure_doesNotBlockHomeOrClearLocalSession() = runBlocking {
        val releaseNetworkValidation = CompletableDeferred<Unit>()
        val fixture = fixture(
            session = authSession(expiresAt = Long.MAX_VALUE),
            refreshFailure = IOException("network unavailable"),
            refreshGate = releaseNetworkValidation,
            outletFailure = IOException("network unavailable"),
            shiftFailure = IOException("network unavailable"),
        )

        fixture.viewModel()
        dispatcher.scheduler.runCurrent()

        val startupState = fixture.state() as SessionState.Authenticated
        assertEquals(Screen.Home.route, startupRouteFor(startupState))
        assertEquals("user-id", fixture.authSessionDataStore.getSession()?.userId)

        releaseNetworkValidation.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        val state = fixture.state() as SessionState.Authenticated
        assertEquals(Screen.Home.route, startupRouteFor(state))
        assertEquals("user-id", fixture.authSessionDataStore.getSession()?.userId)
        assertEquals(1, fixture.api.refreshCalls)
        assertEquals(1, fixture.shiftRepository.getCurrentShiftCalls)
        assertNull(fixture.outletRepository.selectedOutlet.value)
        assertEquals(1, fixture.cartRepository.getCart().value.totalItems())
    }

    @Test
    fun authenticationRejection_invalidatesSessionAndClearsBoundaryState() = runBlocking {
        val fixture = fixture(
            session = authSession(expiresAt = Long.MAX_VALUE),
            refreshFailure = HttpException(
                Response.error<AuthTokenResponse>(401, "".toResponseBody()),
            ),
        )

        fixture.viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(SessionState.Unauthenticated, fixture.state())
        assertNull(fixture.authSessionDataStore.getSession())
        assertNull(fixture.outletRepository.selectedOutlet.value)
        assertEquals(0, fixture.cartRepository.getCart().value.totalItems())
        assertNull(fixture.shiftRepository.currentShift.value)
    }

    @Test
    fun outletRestoration_ignoresOutletFromAnotherTenant() = runBlocking {
        val fixture = fixture(
            session = authSession(expiresAt = Long.MAX_VALUE),
            restoredOutlet = outlet().copy(tenantId = "other-tenant"),
        )

        fixture.viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(fixture.outletRepository.selectedOutlet.value)
    }

    @Test
    fun delayedOutletRestoration_doesNotCrossAccountBoundary() = runBlocking {
        val releaseOutletRestoration = CompletableDeferred<Unit>()
        val fixture = fixture(
            session = authSession(expiresAt = Long.MAX_VALUE),
            outletGate = releaseOutletRestoration,
        )

        fixture.viewModel()
        dispatcher.scheduler.runCurrent()
        fixture.authSessionDataStore.clearSession()
        fixture.authSessionDataStore.saveSession(
            authSession(expiresAt = Long.MAX_VALUE).copy(
                tenantId = "other-tenant",
                userId = "other-user",
            ),
        )
        releaseOutletRestoration.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(fixture.outletRepository.selectedOutlet.value)
    }

    @Test
    fun accountIdentityChange_clearsVolatileBusinessState() = runBlocking {
        val fixture = fixture(
            session = authSession(expiresAt = Long.MAX_VALUE),
        )
        fixture.viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        fixture.authSessionDataStore.clearSession()
        fixture.authSessionDataStore.saveSession(
            authSession(expiresAt = Long.MAX_VALUE).copy(
                tenantId = "other-tenant",
                userId = "other-user",
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(fixture.outletRepository.selectedOutlet.value)
        assertEquals(0, fixture.cartRepository.getCart().value.totalItems())
        assertNull(fixture.shiftRepository.currentShift.value)
    }

    @Test
    fun offlineStartup_restoresOperationalContextLocally() = runBlocking {
        val fixture = fixture(
            session = authSession(expiresAt = Long.MAX_VALUE),
            refreshFailure = IOException("network unavailable"),
            outletFailure = IOException("network unavailable"),
            shiftFailure = IOException("network unavailable"),
        )

        fixture.viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fixture.outletRepository.restoreCalls)
        assertEquals(1, fixture.shiftRepository.restoreCalls)
        assertEquals("outlet-id", fixture.shiftRepository.lastExpectedOutletId)
    }

    @Test
    fun startupOperationalContextRestoreFailure_doesNotBlockHomeOrCrash() = runBlocking {
        val fixture = fixture(
            session = authSession(expiresAt = Long.MAX_VALUE),
            refreshFailure = IOException("network unavailable"),
            outletFailure = IOException("network unavailable"),
            shiftFailure = IOException("network unavailable"),
        )
        fixture.outletRepository.restoreFailure = IOException("Corrupt disk read")

        val viewModel = fixture.viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = fixture.state() as SessionState.Authenticated
        assertEquals(Screen.Home.route, startupRouteFor(state))
        assertEquals("user-id", (state.session as NavigationSession.AuthV2).value.userId)
    }

    @Test
    fun onLoggedOut_clearsCurrentUserState() {
        val fixture = fixture(
            session = authSession(expiresAt = Long.MAX_VALUE),
        )
        val viewModel = fixture.viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onLoggedOut()

        assertEquals(SessionState.Unauthenticated, fixture.state())
        assertEquals(AuthV2Screen.Graph.route, startupRouteFor(fixture.state()))
    }

    private fun fixture(
        session: AuthSession? = null,
        refreshFailure: Throwable? = null,
        refreshGate: CompletableDeferred<Unit>? = null,
        outletFailure: Throwable? = null,
        outletGate: CompletableDeferred<Unit>? = null,
        restoredOutlet: Outlet = outlet(),
        shiftFailure: Throwable? = null,
    ): Fixture {
        val authSessionDataStore = AuthSessionDataStore(InMemoryPreferencesDataStore())
        session?.let { storedSession ->
            runBlocking { authSessionDataStore.saveSession(storedSession) }
        }
        val api = FakeAuthV2Api(
            refreshFailure = refreshFailure,
            refreshGate = refreshGate,
        )
        val coordinator = RefreshTokenCoordinator(
            authV2Api = api,
            authSessionDataStore = authSessionDataStore,
            currentTimeMillis = { NOW_MILLIS },
        )
        val outletRepository = FakeOutletRepository(
            getOutletsFailure = outletFailure,
            getOutletsGate = outletGate,
            restoredOutlet = restoredOutlet,
        )
        val cartRepository = CartRepositoryImpl().apply {
            addProduct(product())
        }
        val shiftRepository = FakeShiftRepository(shiftFailure)
        return Fixture(
            authSessionDataStore = authSessionDataStore,
            refreshTokenCoordinator = coordinator,
            api = api,
            outletRepository = outletRepository,
            cartRepository = cartRepository,
            shiftRepository = shiftRepository,
        )
    }

    private class Fixture(
        val authSessionDataStore: AuthSessionDataStore,
        private val refreshTokenCoordinator: RefreshTokenCoordinator,
        val api: FakeAuthV2Api,
        val outletRepository: FakeOutletRepository,
        val cartRepository: CartRepositoryImpl,
        val shiftRepository: FakeShiftRepository,
    ) {
        private lateinit var viewModel: AppNavigationViewModel

        fun viewModel(): AppNavigationViewModel = AppNavigationViewModel(
            authSessionDataStore = authSessionDataStore,
            refreshTokenCoordinator = refreshTokenCoordinator,
            outletRepository = outletRepository,
            shiftRepository = shiftRepository,
            sessionBoundaryCleaner = SessionBoundaryCleaner(
                cartRepository = cartRepository,
                outletRepository = outletRepository,
                shiftRepository = shiftRepository,
            ),
        ).also { created -> viewModel = created }

        fun state(): SessionState = viewModel.sessionState.value
    }

    private class FakeOutletRepository(
        private val getOutletsFailure: Throwable? = null,
        private val getOutletsGate: CompletableDeferred<Unit>? = null,
        private val restoredOutlet: Outlet = outlet(),
    ) : OutletRepository {
        private val outletState = MutableStateFlow<Outlet?>(null)
        override val selectedOutlet: StateFlow<Outlet?> = outletState

        override suspend fun getOutlets(): Result<List<Outlet>> {
            getOutletsGate?.await()
            return getOutletsFailure
                ?.let(Result.Companion::failure)
                ?: Result.success(listOf(restoredOutlet))
        }

        override suspend fun selectOutlet(outlet: Outlet) {
            outletState.value = outlet
        }

        override suspend fun clearSelectedOutlet(tenantId: String?, userId: String?) {
            outletState.value = null
        }

        var restoreCalls = 0
        var restoreFailure: Throwable? = null
        override suspend fun restoreSelectedOutlet() {
            restoreFailure?.let { throw it }
            restoreCalls += 1
        }
    }

    private class FakeShiftRepository(
        private val getCurrentShiftFailure: Throwable? = null,
    ) : ShiftRepository {
        override val currentShift = MutableStateFlow<Shift?>(shift())
        var getCurrentShiftCalls = 0

        override suspend fun getCurrentShift(): Result<Shift?> {
            getCurrentShiftCalls += 1
            return getCurrentShiftFailure
                ?.let(Result.Companion::failure)
                ?: Result.success(currentShift.value)
        }

        override suspend fun openShift(
            outletId: String,
            openingCash: Long,
        ): Result<Shift> = error("Not used")

        override suspend fun closeShift(
            shiftId: String,
            closingCash: Long,
        ): Result<Shift> = error("Not used")

        override suspend fun clearCurrentShift(tenantId: String?, userId: String?) {
            currentShift.value = null
        }

        var restoreCalls = 0
        var lastExpectedOutletId: String? = null
        override suspend fun restoreCurrentShift(expectedOutletId: String?) {
            lastExpectedOutletId = expectedOutletId
            restoreCalls += 1
        }
    }

    private class FakeAuthV2Api(
        private val refreshFailure: Throwable?,
        private val refreshGate: CompletableDeferred<Unit>?,
    ) : AuthV2Api {
        var refreshCalls = 0
        var lastRefreshToken: String? = null

        override suspend fun resolveStore(request: StoreResolveRequest): StoreResolveResponse =
            error("Not used")

        override suspend fun pinLogin(request: PinLoginRequest): AuthTokenResponse =
            error("Not used")

        override suspend fun refreshToken(request: RefreshTokenRequest): AuthTokenResponse {
            refreshCalls += 1
            lastRefreshToken = request.refreshToken
            refreshGate?.await()
            refreshFailure?.let { throwable -> throw throwable }
            return AuthTokenResponse(
                accessToken = "new-access-token",
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
        const val NOW_MILLIS = 1_000_000L

        fun authSession(expiresAt: Long) = AuthSession(
            userId = "user-id",
            userName = "Kasir Utama",
            tenantId = "tenant-id",
            role = UserRole.CASHIER,
            outletId = "outlet-id",
            accessToken = "old-access-token",
            refreshToken = "old-refresh-token",
            expiresAt = expiresAt,
            deviceId = "8f1fbf18-ed1e-4db8-a78d-857238e08e62",
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
