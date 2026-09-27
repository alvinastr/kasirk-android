package com.kasirkita.pos.presentation.navigation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.core.datastore.LegacySessionReader
import com.kasirkita.pos.core.network.RefreshTokenCoordinator
import com.kasirkita.pos.data.api.AuthV2Api
import com.kasirkita.pos.data.model.AuthTokenResponse
import com.kasirkita.pos.data.model.CurrentUserResponse
import com.kasirkita.pos.data.model.LogoutRequest
import com.kasirkita.pos.data.model.PinLoginRequest
import com.kasirkita.pos.data.model.RefreshTokenRequest
import com.kasirkita.pos.data.model.StoreResolveRequest
import com.kasirkita.pos.data.model.StoreResolveResponse
import com.kasirkita.pos.domain.model.AuthSession
import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.domain.model.UserRole
import com.kasirkita.pos.domain.model.UserSession
import com.kasirkita.pos.domain.repository.OutletRepository
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
    fun validAuthV2Session_entersAuthenticatedStateWithoutRefresh() = runBlocking {
        val fixture = fixture(
            session = authSession(expiresAt = Long.MAX_VALUE),
        )

        fixture.viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = fixture.state() as SessionState.Authenticated
        val navigationSession = state.session as NavigationSession.AuthV2
        assertEquals("user-id", navigationSession.value.userId)
        assertEquals(Screen.Outlet.route, startupRouteFor(state))
        assertEquals(0, fixture.api.refreshCalls)
    }

    @Test
    fun expiredSession_refreshSuccess_updatesSessionAndAuthenticates() = runBlocking {
        val fixture = fixture(
            session = authSession(expiresAt = 0L),
        )

        fixture.viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = fixture.state() as SessionState.Authenticated
        val navigationSession = state.session as NavigationSession.AuthV2
        val storedSession = requireNotNull(fixture.authSessionDataStore.getSession())
        assertEquals(1, fixture.api.refreshCalls)
        assertEquals("old-refresh-token", fixture.api.lastRefreshToken)
        assertEquals("new-access-token", navigationSession.value.accessToken)
        assertEquals("new-access-token", storedSession.accessToken)
        assertEquals("new-refresh-token", storedSession.refreshToken)
    }

    @Test
    fun expiredSession_refreshFailure_clearsSessionAndBecomesUnauthenticated() = runBlocking {
        val fixture = fixture(
            session = authSession(expiresAt = 0L),
            refreshFailure = IOException("network unavailable"),
            legacySession = legacySession(),
        )

        fixture.viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(SessionState.Unauthenticated, fixture.state())
        assertNull(fixture.authSessionDataStore.getSession())
        assertEquals(AuthV2Screen.Graph.route, startupRouteFor(fixture.state()))
    }

    @Test
    fun noAuthV2Session_usesLegacySessionAsMigrationFallback() {
        val fixture = fixture(legacySession = legacySession())

        fixture.viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = fixture.state() as SessionState.Authenticated
        assertTrue(state.session is NavigationSession.AuthV1)
        assertEquals(UserRole.ADMIN, state.session.role)
    }

    private fun fixture(
        session: AuthSession? = null,
        refreshFailure: Throwable? = null,
        legacySession: UserSession? = null,
    ): Fixture {
        val authSessionDataStore = AuthSessionDataStore(InMemoryPreferencesDataStore())
        session?.let { storedSession ->
            runBlocking { authSessionDataStore.saveSession(storedSession) }
        }
        val api = FakeAuthV2Api(refreshFailure)
        val coordinator = RefreshTokenCoordinator(
            authV2Api = api,
            authSessionDataStore = authSessionDataStore,
            currentTimeMillis = { NOW_MILLIS },
        )
        return Fixture(
            authSessionDataStore = authSessionDataStore,
            legacySessionReader = FakeLegacySessionReader(legacySession),
            refreshTokenCoordinator = coordinator,
            api = api,
        )
    }

    private class Fixture(
        val authSessionDataStore: AuthSessionDataStore,
        private val legacySessionReader: LegacySessionReader,
        private val refreshTokenCoordinator: RefreshTokenCoordinator,
        val api: FakeAuthV2Api,
    ) {
        private lateinit var viewModel: AppNavigationViewModel

        fun viewModel(): AppNavigationViewModel = AppNavigationViewModel(
            authSessionDataStore = authSessionDataStore,
            legacySessionReader = legacySessionReader,
            refreshTokenCoordinator = refreshTokenCoordinator,
            outletRepository = FakeOutletRepository(),
        ).also { created -> viewModel = created }

        fun state(): SessionState = viewModel.sessionState.value
    }

    private class FakeLegacySessionReader(
        private val session: UserSession?,
    ) : LegacySessionReader {
        override suspend fun getSession(): UserSession? = session
    }

    private class FakeOutletRepository : OutletRepository {
        override val selectedOutlet: StateFlow<Outlet?> = MutableStateFlow(null)

        override suspend fun getOutlets(): Result<List<Outlet>> = Result.success(emptyList())

        override fun selectOutlet(outlet: Outlet) = Unit
    }

    private class FakeAuthV2Api(
        private val refreshFailure: Throwable?,
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

        fun legacySession() = UserSession(
            accessToken = "legacy-access-token",
            tenantId = "legacy-tenant-id",
            userId = "legacy-user-id",
            role = UserRole.ADMIN,
        )
    }
}
