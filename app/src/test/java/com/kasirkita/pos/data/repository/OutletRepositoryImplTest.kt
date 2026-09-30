package com.kasirkita.pos.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.core.datastore.OperationalContextDataStore
import com.kasirkita.pos.data.api.OutletApi
import com.kasirkita.pos.data.model.OutletResponse
import com.kasirkita.pos.domain.model.AuthSession
import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.domain.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class OutletRepositoryImplTest {

    @Test
    fun selectOutlet_publishesAndPersistsLocally() = runBlocking {
        val authPreferences = InMemoryPreferencesDataStore()
        val opPreferences = InMemoryPreferencesDataStore()
        val authDataStore = AuthSessionDataStore(authPreferences)
        val opDataStore = OperationalContextDataStore(opPreferences)

        authDataStore.saveSession(session())
        val repository = OutletRepositoryImpl(FakeOutletApi(), authDataStore, opDataStore)

        val selected = outlet()
        repository.selectOutlet(selected)

        assertEquals(selected, repository.selectedOutlet.value)

        val second = OutletRepositoryImpl(FakeOutletApi(), authDataStore, opDataStore)
        second.restoreSelectedOutlet()

        assertEquals(selected, second.selectedOutlet.value)
    }

    @Test
    fun clearSelectedOutlet_clearsMemoryAndLocalStore() = runBlocking {
        val authPreferences = InMemoryPreferencesDataStore()
        val opPreferences = InMemoryPreferencesDataStore()
        val authDataStore = AuthSessionDataStore(authPreferences)
        val opDataStore = OperationalContextDataStore(opPreferences)

        authDataStore.saveSession(session())
        val repository = OutletRepositoryImpl(FakeOutletApi(), authDataStore, opDataStore)

        repository.selectOutlet(outlet())
        repository.clearSelectedOutlet()

        assertNull(repository.selectedOutlet.value)

        val second = OutletRepositoryImpl(FakeOutletApi(), authDataStore, opDataStore)
        second.restoreSelectedOutlet()

        assertNull(second.selectedOutlet.value)
    }

    @Test
    fun clearSelectedOutlet_withExplicitIdentity_clearsStoreEvenWhenAuthSessionNull() = runBlocking {
        val authPreferences = InMemoryPreferencesDataStore()
        val opPreferences = InMemoryPreferencesDataStore()
        val authDataStore = AuthSessionDataStore(authPreferences)
        val opDataStore = OperationalContextDataStore(opPreferences)

        authDataStore.saveSession(session())
        val repository = OutletRepositoryImpl(FakeOutletApi(), authDataStore, opDataStore)

        repository.selectOutlet(outlet())
        authDataStore.clearSession()
        assertNull(authDataStore.getSession())

        repository.clearSelectedOutlet(tenantId = "tenant-id", userId = "user-id")

        assertNull(opDataStore.getOutlet("tenant-id", "user-id"))
    }

    @Test
    fun restoreSelectedOutlet_doesNotRestoreForDifferentTenant() = runBlocking {
        val authPreferences = InMemoryPreferencesDataStore()
        val opPreferences = InMemoryPreferencesDataStore()
        val authDataStore = AuthSessionDataStore(authPreferences)
        val opDataStore = OperationalContextDataStore(opPreferences)

        authDataStore.saveSession(session())
        val first = OutletRepositoryImpl(FakeOutletApi(), authDataStore, opDataStore)
        first.selectOutlet(outlet())

        authDataStore.clearSession()
        authDataStore.saveSession(session().copy(tenantId = "other-tenant", userId = "other-user"))

        val second = OutletRepositoryImpl(FakeOutletApi(), authDataStore, opDataStore)
        second.restoreSelectedOutlet()

        assertNull(second.selectedOutlet.value)
    }

    @Test
    fun getOutlets_returnsApiResult() = runBlocking {
        val api = FakeOutletApi(listOf(outletResponse()))
        val repository = OutletRepositoryImpl(
            api,
            AuthSessionDataStore(InMemoryPreferencesDataStore()),
            OperationalContextDataStore(InMemoryPreferencesDataStore()),
        )

        val result = repository.getOutlets()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals("outlet-id", result.getOrThrow().first().id)
    }

    @Test
    fun selectOutlet_persistsCompletelyBeforeReturning_simulatesProcessDeath() = runBlocking {
        val authPreferences = InMemoryPreferencesDataStore()
        val opPreferences = InMemoryPreferencesDataStore()
        val authDataStore = AuthSessionDataStore(authPreferences)
        val opDataStore = OperationalContextDataStore(opPreferences)

        authDataStore.saveSession(session())
        val repository = OutletRepositoryImpl(FakeOutletApi(), authDataStore, opDataStore)

        val selected = outlet()
        repository.selectOutlet(selected)
        // selectOutlet completes after DataStore write (NonCancellable ensures it)

        // Simulate process death by creating new instances
        val newAuthDataStore = AuthSessionDataStore(authPreferences)
        val newOpDataStore = OperationalContextDataStore(opPreferences)
        val secondRepository = OutletRepositoryImpl(FakeOutletApi(), newAuthDataStore, newOpDataStore)

        secondRepository.restoreSelectedOutlet()

        // Verify outlet was persisted and can be restored
        assertEquals(selected, secondRepository.selectedOutlet.value)
    }

    private class FakeOutletApi(
        private val outlets: List<OutletResponse> = emptyList(),
    ) : OutletApi {
        override suspend fun getOutlets(): List<OutletResponse> = outlets
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
        fun session() = AuthSession(
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

        fun outletResponse() = OutletResponse(
            id = "outlet-id",
            tenantId = "tenant-id",
            name = "Outlet Utama",
            address = null,
            isActive = true,
            createdAt = "2026-09-27T00:00:00Z",
        )
    }
}
