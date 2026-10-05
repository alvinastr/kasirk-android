package com.kasirkita.pos.core.session

import com.kasirkita.pos.core.datastore.AuthSessionDataStoreTestHelper

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.core.datastore.OperationalContextDataStore
import com.kasirkita.pos.data.api.OutletApi
import com.kasirkita.pos.data.api.ShiftApi
import com.kasirkita.pos.data.repository.OutletRepositoryImpl
import com.kasirkita.pos.data.repository.ShiftRepositoryImpl
import com.kasirkita.pos.domain.model.AuthSession
import com.kasirkita.pos.domain.model.Cart
import com.kasirkita.pos.domain.model.CartModifierSelectionSnapshot
import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.model.Shift
import com.kasirkita.pos.domain.model.UserRole
import com.kasirkita.pos.domain.repository.CartRepository
import com.kasirkita.pos.domain.repository.CartUpdateResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

class SessionBoundaryCleanerTest {

    @Test
    fun clear_removesPersistedDataForCurrentSessionBeforeAuthCleared() = runBlocking {
        val authPreferences = InMemoryPreferencesDataStore()
        val opPreferences = InMemoryPreferencesDataStore()
        val authDataStore = AuthSessionDataStoreTestHelper.createTestStore(authPreferences)
        val opDataStore = OperationalContextDataStore(opPreferences)

        val session = session("tenant-1", "user-1")
        authDataStore.saveSession(session)
        
        val outletRepo = OutletRepositoryImpl(
            outletApi = FakeOutletApi(),
            authSessionDataStore = authDataStore,
            operationalContextDataStore = opDataStore,
        )
        val shiftRepo = ShiftRepositoryImpl(
            shiftApi = FakeShiftApi(),
            authSessionDataStore = authDataStore,
            operationalContextDataStore = opDataStore,
        )
        
        outletRepo.selectOutlet(outlet("outlet-1", "tenant-1"))
        val shift = Shift(
            id = "shift-1",
            outletId = "outlet-1",
            userId = "user-1",
            openingCash = 50_000L,
            closingCash = null,
            expectedCash = null,
            difference = null,
            status = "OPEN",
            openedAt = "2024-01-01T00:00:00Z",
            closedAt = null,
        )
        opDataStore.saveShift("tenant-1", "user-1", shift)

        val cartRepo = FakeCartRepository()
        val cleaner = SessionBoundaryCleaner(cartRepo, outletRepo, shiftRepo)
        
        cleaner.clear()
        authDataStore.clearSession()

        val persistedOutlet = opDataStore.getOutlet("tenant-1", "user-1")
        val persistedShift = opDataStore.getShift("tenant-1", "user-1")
        
        assertNull("Persisted outlet should be cleared before auth cleared", persistedOutlet)
        assertNull("Persisted shift should be cleared before auth cleared", persistedShift)
    }

    @Test
    fun clear_removesOldSessionDataAfterReloginWithCaptureBeforeClear() = runBlocking {
        val authPreferences = InMemoryPreferencesDataStore()
        val opPreferences = InMemoryPreferencesDataStore()
        val authDataStore = AuthSessionDataStoreTestHelper.createTestStore(authPreferences)
        val opDataStore = OperationalContextDataStore(opPreferences)

        val session1 = session("tenant-1", "user-1")
        authDataStore.saveSession(session1)
        
        val outletRepo = OutletRepositoryImpl(
            outletApi = FakeOutletApi(),
            authSessionDataStore = authDataStore,
            operationalContextDataStore = opDataStore,
        )
        val shiftRepo = ShiftRepositoryImpl(
            shiftApi = FakeShiftApi(),
            authSessionDataStore = authDataStore,
            operationalContextDataStore = opDataStore,
        )
        
        outletRepo.selectOutlet(outlet("outlet-1", "tenant-1"))
        opDataStore.saveShift("tenant-1", "user-1", shift("shift-1", "outlet-1", "user-1"))

        val cartRepo = FakeCartRepository()
        val cleaner = SessionBoundaryCleaner(cartRepo, outletRepo, shiftRepo)
        
        cleaner.clear()
        authDataStore.clearSession()

        val session2 = session("tenant-2", "user-2")
        authDataStore.saveSession(session2)
        
        val persistedOutletOld = opDataStore.getOutlet("tenant-1", "user-1")
        val persistedShiftOld = opDataStore.getShift("tenant-1", "user-1")
        
        assertNull("Old session outlet data should be cleared", persistedOutletOld)
        assertNull("Old session shift data should be cleared", persistedShiftOld)
    }

    @Test
    fun clear_withExplicitIdentity_clearsOperationalContextEvenWhenAuthSessionAlreadyEmpty() = runBlocking {
        val authPreferences = InMemoryPreferencesDataStore()
        val opPreferences = InMemoryPreferencesDataStore()
        val authDataStore = AuthSessionDataStoreTestHelper.createTestStore(authPreferences)
        val opDataStore = OperationalContextDataStore(opPreferences)

        val session = session("tenant-1", "user-1")
        authDataStore.saveSession(session)

        val outletRepo = OutletRepositoryImpl(
            outletApi = FakeOutletApi(),
            authSessionDataStore = authDataStore,
            operationalContextDataStore = opDataStore,
        )
        val shiftRepo = ShiftRepositoryImpl(
            shiftApi = FakeShiftApi(),
            authSessionDataStore = authDataStore,
            operationalContextDataStore = opDataStore,
        )

        outletRepo.selectOutlet(outlet("outlet-1", "tenant-1"))
        opDataStore.saveShift("tenant-1", "user-1", shift("shift-1", "outlet-1", "user-1"))

        // Simulate session cleared first (e.g. 401 token expiry)
        authDataStore.clearSession()
        assertNull(authDataStore.getSession())

        val cartRepo = FakeCartRepository()
        val cleaner = SessionBoundaryCleaner(cartRepo, outletRepo, shiftRepo)

        // Clear using previous identity
        cleaner.clear(tenantId = "tenant-1", userId = "user-1")

        val persistedOutlet = opDataStore.getOutlet("tenant-1", "user-1")
        val persistedShift = opDataStore.getShift("tenant-1", "user-1")

        assertNull("Persisted outlet must be cleared using previous identity", persistedOutlet)
        assertNull("Persisted shift must be cleared using previous identity", persistedShift)
    }

    private class FakeOutletApi : OutletApi {
        override suspend fun getOutlets(): List<com.kasirkita.pos.data.model.OutletResponse> = error("Not used")
    }

    private class FakeShiftApi : ShiftApi {
        override suspend fun getCurrentShift(): Response<com.kasirkita.pos.data.model.ShiftResponse> = error("Not used")
        override suspend fun openShift(request: com.kasirkita.pos.data.model.OpenShiftRequest): Response<com.kasirkita.pos.data.model.ShiftResponse> = error("Not used")
        override suspend fun getShiftSummary(shiftId: String): Response<com.kasirkita.pos.data.model.ShiftSummaryResponse> = error("Not used")
        override suspend fun closeShift(shiftId: String, request: com.kasirkita.pos.data.model.CloseShiftRequest): Response<com.kasirkita.pos.data.model.ShiftResponse> = error("Not used")
    }

    private class FakeCartRepository : CartRepository {
        override fun addProduct(product: Product, availableStock: Int?): CartUpdateResult = CartUpdateResult.UPDATED
        override fun addConfiguredProduct(
            product: Product,
            selectedModifiers: List<CartModifierSelectionSnapshot>,
            note: String?,
            availableStock: Int?,
        ): CartUpdateResult = CartUpdateResult.UPDATED
        override fun removeProduct(lineKey: String) = Unit
        override fun updateQuantity(lineKey: String, quantity: Int): CartUpdateResult = CartUpdateResult.UPDATED
        override fun getCart(): StateFlow<Cart> = MutableStateFlow(Cart(emptyList()))
        override fun clearCart() = Unit
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
        fun session(tenantId: String, userId: String) = AuthSession(
            userId = userId,
            userName = "User Name",
            tenantId = tenantId,
            role = UserRole.CASHIER,
            outletId = "outlet-1",
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresAt = Long.MAX_VALUE,
            deviceId = "device-id",
        )

        fun outlet(id: String, tenantId: String) = Outlet(
            id = id,
            tenantId = tenantId,
            name = "Outlet Name",
            address = null,
            isActive = true,
            createdAt = "2024-01-01T00:00:00Z",
        )

        fun shift(id: String, outletId: String, userId: String) = Shift(
            id = id,
            outletId = outletId,
            userId = userId,
            openingCash = 50_000L,
            closingCash = null,
            expectedCash = null,
            difference = null,
            status = "OPEN",
            openedAt = "2024-01-01T00:00:00Z",
            closedAt = null,
        )
    }
}
