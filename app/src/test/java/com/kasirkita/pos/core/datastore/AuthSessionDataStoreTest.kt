package com.kasirkita.pos.core.datastore

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kasirkita.pos.core.security.FakeSecureTokenStorage
import com.kasirkita.pos.domain.model.AuthSession
import com.kasirkita.pos.domain.model.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class AuthSessionDataStoreTest {

    @Test
    fun session_isPersistedAndCanBeReadByANewStoreInstance() = runBlocking {
        val preferences = InMemoryPreferencesDataStore()
        val firstStore = AuthSessionDataStoreTestHelper.createTestStore(preferences)
        val expected = session()

        firstStore.saveSession(expected)

        val restored = AuthSessionDataStoreTestHelper.createTestStore(preferences).getSession()
        assertEquals(expected, restored)
    }

    @Test
    fun saveSession_persistsEncryptedTokensAndRemovesPlaintextKeys() = runBlocking {
        val preferences = InMemoryPreferencesDataStore()
        val store = AuthSessionDataStoreTestHelper.createTestStore(preferences)

        store.saveSession(session())
        val raw = preferences.data.first()

        assertNull(raw[LEGACY_ACCESS_TOKEN])
        assertNull(raw[LEGACY_REFRESH_TOKEN])
        assertTrue(raw[ENCRYPTED_ACCESS_TOKEN]!!.startsWith("v1:"))
        assertTrue(raw[ENCRYPTED_REFRESH_TOKEN]!!.startsWith("v1:"))
        assertFalse(raw[ENCRYPTED_ACCESS_TOKEN] == "access-token")
        assertFalse(raw[ENCRYPTED_REFRESH_TOKEN] == "refresh-token")
    }

    @Test
    fun completeLegacyPlaintextPair_migratesAndPreservesMetadataAndDeviceId() = runBlocking {
        val preferences = legacyPreferences()
        val store = AuthSessionDataStoreTestHelper.createTestStore(preferences)

        val migrated = store.getSession()
        val raw = preferences.data.first()

        assertEquals(session(), migrated)
        assertNull(raw[LEGACY_ACCESS_TOKEN])
        assertNull(raw[LEGACY_REFRESH_TOKEN])
        assertTrue(raw[ENCRYPTED_ACCESS_TOKEN]!!.startsWith("v1:"))
        assertTrue(raw[ENCRYPTED_REFRESH_TOKEN]!!.startsWith("v1:"))
        assertEquals("user-id", raw[USER_ID])
        assertEquals("tenant-id", raw[TENANT_ID])
        assertEquals("8f1fbf18-ed1e-4db8-a78d-857238e08e62", raw[DEVICE_ID])
    }

    @Test
    fun migration_isIdempotent() = runBlocking {
        val preferences = legacyPreferences()
        val store = AuthSessionDataStoreTestHelper.createTestStore(preferences)

        assertEquals(session(), store.getSession())
        val encryptedAccess = preferences.data.first()[ENCRYPTED_ACCESS_TOKEN]
        assertEquals(session(), store.getSession())

        assertEquals(encryptedAccess, preferences.data.first()[ENCRYPTED_ACCESS_TOKEN])
    }

    @Test
    fun encryptedPairWithLeftoverLegacyKeys_keepsEncryptedPairAndRemovesLegacy() = runBlocking {
        val preferences = InMemoryPreferencesDataStore()
        val store = AuthSessionDataStoreTestHelper.createTestStore(preferences)
        store.saveSession(session(accessToken = "encrypted-access", refreshToken = "encrypted-refresh"))
        preferences.edit {
            it[LEGACY_ACCESS_TOKEN] = "stale-access"
            it[LEGACY_REFRESH_TOKEN] = "stale-refresh"
        }

        val restored = store.getSession()
        val raw = preferences.data.first()

        assertEquals("encrypted-access", restored?.accessToken)
        assertEquals("encrypted-refresh", restored?.refreshToken)
        assertNull(raw[LEGACY_ACCESS_TOKEN])
        assertNull(raw[LEGACY_REFRESH_TOKEN])
    }

    @Test
    fun partialLegacyState_failsClosedAndPreservesDeviceId() = runBlocking {
        val preferences = legacyPreferences()
        preferences.edit { it.remove(LEGACY_REFRESH_TOKEN) }
        val store = AuthSessionDataStoreTestHelper.createTestStore(preferences)

        assertNull(store.getSession())
        val raw = preferences.data.first()
        assertNull(raw[USER_ID])
        assertNull(raw[LEGACY_ACCESS_TOKEN])
        assertEquals("8f1fbf18-ed1e-4db8-a78d-857238e08e62", raw[DEVICE_ID])
    }

    @Test
    fun partialEncryptedState_failsClosedAndPreservesDeviceId() = runBlocking {
        val preferences = InMemoryPreferencesDataStore()
        val store = AuthSessionDataStoreTestHelper.createTestStore(preferences)
        store.saveSession(session())
        preferences.edit { it.remove(ENCRYPTED_REFRESH_TOKEN) }

        assertNull(store.getSession())
        val raw = preferences.data.first()
        assertNull(raw[USER_ID])
        assertNull(raw[ENCRYPTED_ACCESS_TOKEN])
        assertEquals("8f1fbf18-ed1e-4db8-a78d-857238e08e62", raw[DEVICE_ID])
    }

    @Test
    fun corruptEncryptedToken_failsClosedAndPreservesDeviceId() = runBlocking {
        val preferences = InMemoryPreferencesDataStore()
        val store = AuthSessionDataStoreTestHelper.createTestStore(preferences)
        store.saveSession(session())
        preferences.edit { it[ENCRYPTED_ACCESS_TOKEN] = "v1:bad:%%%" }

        assertNull(store.getSession())
        val raw = preferences.data.first()
        assertNull(raw[USER_ID])
        assertNull(raw[ENCRYPTED_ACCESS_TOKEN])
        assertEquals("8f1fbf18-ed1e-4db8-a78d-857238e08e62", raw[DEVICE_ID])
    }

    @Test
    fun clearSession_removesSessionButKeepsStableDeviceId() = runBlocking {
        val preferences = InMemoryPreferencesDataStore()
        val store = AuthSessionDataStoreTestHelper.createTestStore(preferences)
        val provider = DeviceIdProvider(store)
        val deviceId = provider.getDeviceId()
        store.saveSession(session(deviceId = deviceId))
        preferences.edit {
            it[LEGACY_ACCESS_TOKEN] = "legacy-access"
            it[LEGACY_REFRESH_TOKEN] = "legacy-refresh"
        }

        store.clearSession()

        val raw = preferences.data.first()
        assertNull(store.getSession())
        assertNull(raw[LEGACY_ACCESS_TOKEN])
        assertNull(raw[LEGACY_REFRESH_TOKEN])
        assertNull(raw[ENCRYPTED_ACCESS_TOKEN])
        assertNull(raw[ENCRYPTED_REFRESH_TOKEN])
        assertEquals(deviceId, DeviceIdProvider(AuthSessionDataStoreTestHelper.createTestStore(preferences)).getDeviceId())
    }

    @Test
    fun updateTokenPair_changesOnlyTokenValuesAndExpiry() = runBlocking {
        val preferences = InMemoryPreferencesDataStore()
        val store = AuthSessionDataStoreTestHelper.createTestStore(preferences)
        val original = session()
        store.saveSession(original)

        val tokenPairUpdated = store.updateTokenPair(
            expectedIdentity = original.toSessionIdentity(),
            expectedRefreshToken = original.refreshToken,
            accessToken = "new-access-token",
            refreshToken = "new-refresh-token",
            expiresAt = 2_000L,
        )

        assertTrue(tokenPairUpdated)
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
        val raw = preferences.data.first()
        assertNull(raw[LEGACY_ACCESS_TOKEN])
        assertNull(raw[LEGACY_REFRESH_TOKEN])
        assertTrue(raw[ENCRYPTED_ACCESS_TOKEN]!!.startsWith("v1:"))
        assertTrue(raw[ENCRYPTED_REFRESH_TOKEN]!!.startsWith("v1:"))
    }

    @Test
    fun staleTokenUpdate_doesNotOverwriteANewerSession() = runBlocking {
        val store = AuthSessionDataStoreTestHelper.createTestStore()
        val original = session()
        val replacement = original.copy(
            accessToken = "replacement-access-token",
            refreshToken = "replacement-refresh-token",
        )
        store.saveSession(original)
        store.saveSession(replacement)

        val updated = store.updateTokenPair(
            expectedIdentity = original.toSessionIdentity(),
            expectedRefreshToken = original.refreshToken,
            accessToken = "stale-access-token",
            refreshToken = "stale-refresh-token",
            expiresAt = 5_000L,
        )

        assertFalse(updated)
        assertEquals(replacement, store.getSession())
    }

    @Test
    fun clearSessionIfMatches_clearsMatchingSession() = runBlocking {
        val preferences = InMemoryPreferencesDataStore()
        val store = AuthSessionDataStoreTestHelper.createTestStore(preferences)
        val original = session()
        store.saveSession(original)

        val cleared = store.clearSessionIfMatches(original.toSessionIdentity(), original.refreshToken)

        assertTrue(cleared)
        assertNull(store.getSession())
        assertNull(preferences.data.first()[ENCRYPTED_REFRESH_TOKEN])
    }

    @Test
    fun conditionalClear_doesNotClearANewerSession() = runBlocking {
        val store = AuthSessionDataStoreTestHelper.createTestStore()
        val original = session()
        val replacement = original.copy(
            accessToken = "replacement-access-token",
            refreshToken = "replacement-refresh-token",
        )
        store.saveSession(original)
        store.saveSession(replacement)

        val cleared = store.clearSessionIfMatches(
            expectedIdentity = original.toSessionIdentity(),
            expectedRefreshToken = original.refreshToken,
        )

        assertFalse(cleared)
        assertEquals(replacement, store.getSession())
    }

    @Test
    fun saveSession_rejectsReplacingADifferentAccount() = runBlocking {
        val store = AuthSessionDataStoreTestHelper.createTestStore()
        store.saveSession(session())

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                store.saveSession(
                    session().copy(
                        tenantId = "other-tenant",
                        userId = "other-user",
                    ),
                )
            }
        }
        Unit
    }

    @Test
    fun deviceId_isValidAndStableAcrossProviderInstances() = runBlocking {
        val preferences = InMemoryPreferencesDataStore()

        val first = DeviceIdProvider(AuthSessionDataStoreTestHelper.createTestStore(preferences)).getDeviceId()
        val second = DeviceIdProvider(AuthSessionDataStoreTestHelper.createTestStore(preferences)).getDeviceId()

        assertEquals(first, UUID.fromString(first).toString())
        assertEquals(first, second)
    }

    private fun legacyPreferences(): InMemoryPreferencesDataStore = InMemoryPreferencesDataStore().also { preferences ->
        runBlocking {
            preferences.edit {
                it[USER_ID] = "user-id"
                it[USER_NAME] = "Kasir Utama"
                it[TENANT_ID] = "tenant-id"
                it[ROLE] = UserRole.CASHIER.name
                it[OUTLET_ID] = "outlet-id"
                it[LEGACY_ACCESS_TOKEN] = "access-token"
                it[LEGACY_REFRESH_TOKEN] = "refresh-token"
                it[EXPIRES_AT] = 1_000L
                it[DEVICE_ID] = "8f1fbf18-ed1e-4db8-a78d-857238e08e62"
            }
        }
    }

    private fun session(
        accessToken: String = "access-token",
        refreshToken: String = "refresh-token",
        deviceId: String = "8f1fbf18-ed1e-4db8-a78d-857238e08e62",
    ) = AuthSession(
        userId = "user-id",
        userName = "Kasir Utama",
        tenantId = "tenant-id",
        role = UserRole.CASHIER,
        outletId = "outlet-id",
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAt = 1_000L,
        deviceId = deviceId,
    )

    private companion object {
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val TENANT_ID = stringPreferencesKey("tenant_id")
        val ROLE = stringPreferencesKey("role")
        val OUTLET_ID = stringPreferencesKey("outlet_id")
        val LEGACY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        val LEGACY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val EXPIRES_AT = longPreferencesKey("expires_at")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val ENCRYPTED_ACCESS_TOKEN = stringPreferencesKey("encrypted_access_token")
        val ENCRYPTED_REFRESH_TOKEN = stringPreferencesKey("encrypted_refresh_token")
    }
}
