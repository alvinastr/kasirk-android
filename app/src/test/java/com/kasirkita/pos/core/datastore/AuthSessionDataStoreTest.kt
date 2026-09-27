package com.kasirkita.pos.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.kasirkita.pos.domain.model.AuthSession
import com.kasirkita.pos.domain.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class AuthSessionDataStoreTest {

    @Test
    fun session_isPersistedAndCanBeReadByANewStoreInstance() = runBlocking {
        val preferences = InMemoryPreferencesDataStore()
        val firstStore = AuthSessionDataStore(preferences)
        val expected = session()

        firstStore.saveSession(expected)

        val restored = AuthSessionDataStore(preferences).getSession()
        assertEquals(expected, restored)
    }

    @Test
    fun clearSession_removesSessionButKeepsStableDeviceId() = runBlocking {
        val preferences = InMemoryPreferencesDataStore()
        val store = AuthSessionDataStore(preferences)
        val provider = DeviceIdProvider(store)
        val deviceId = provider.getDeviceId()
        store.saveSession(session(deviceId = deviceId))

        store.clearSession()

        assertNull(store.getSession())
        assertEquals(deviceId, DeviceIdProvider(AuthSessionDataStore(preferences)).getDeviceId())
    }

    @Test
    fun updateTokenPair_changesOnlyTokenValuesAndExpiry() = runBlocking {
        val store = AuthSessionDataStore(InMemoryPreferencesDataStore())
        val original = session()
        store.saveSession(original)

        store.updateTokenPair(
            accessToken = "new-access-token",
            refreshToken = "new-refresh-token",
            expiresAt = 2_000L,
        )

        val updated = requireNotNull(store.getSession())
        assertEquals(original.userId, updated.userId)
        assertEquals(original.userName, updated.userName)
        assertEquals(original.tenantId, updated.tenantId)
        assertEquals(original.role, updated.role)
        assertEquals(original.outletId, updated.outletId)
        assertEquals(original.deviceId, updated.deviceId)
        assertEquals("new-access-token", updated.accessToken)
        assertEquals("new-refresh-token", updated.refreshToken)
        assertEquals(2_000L, updated.expiresAt)
    }

    @Test
    fun deviceId_isValidAndStableAcrossProviderInstances() = runBlocking {
        val preferences = InMemoryPreferencesDataStore()

        val first = DeviceIdProvider(AuthSessionDataStore(preferences)).getDeviceId()
        val second = DeviceIdProvider(AuthSessionDataStore(preferences)).getDeviceId()

        assertEquals(first, UUID.fromString(first).toString())
        assertEquals(first, second)
    }

    private fun session(
        deviceId: String = "8f1fbf18-ed1e-4db8-a78d-857238e08e62",
    ) = AuthSession(
        userId = "user-id",
        userName = "Kasir Utama",
        tenantId = "tenant-id",
        role = UserRole.CASHIER,
        outletId = "outlet-id",
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresAt = 1_000L,
        deviceId = deviceId,
    )

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
}
