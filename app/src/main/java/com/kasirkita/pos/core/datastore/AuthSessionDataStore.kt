package com.kasirkita.pos.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kasirkita.pos.core.security.SecureTokenStorage
import com.kasirkita.pos.domain.model.AuthSession
import com.kasirkita.pos.domain.model.UserRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transform
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authV2SessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "auth_v2_session",
)

@Singleton
class AuthSessionDataStore internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val secureTokenStorage: SecureTokenStorage,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        secureTokenStorage: SecureTokenStorage,
    ) : this(context.authV2SessionDataStore, secureTokenStorage)

    val sessionFlow: Flow<AuthSession?> = dataStore.data
        .transform { emit(resolveSession()) }
        .distinctUntilChanged()

    suspend fun saveSession(session: AuthSession) {
        val encryptedAccessToken = secureTokenStorage.encrypt(session.accessToken)
        val encryptedRefreshToken = secureTokenStorage.encrypt(session.refreshToken)

        dataStore.edit { preferences ->
            val storedUserId = preferences[USER_ID]?.takeIf(String::isNotBlank)
            val storedTenantId = preferences[TENANT_ID]?.takeIf(String::isNotBlank)
            require(
                storedUserId == null && storedTenantId == null ||
                    storedUserId == session.userId && storedTenantId == session.tenantId,
            ) {
                "A different account session must be cleared before replacement"
            }

            val storedDeviceId = preferences[DEVICE_ID]?.takeIf(String::isNotBlank)
            require(storedDeviceId == null || storedDeviceId == session.deviceId) {
                "Session device ID does not match the stored device ID"
            }

            preferences[USER_ID] = session.userId
            preferences[USER_NAME] = session.userName
            preferences[TENANT_ID] = session.tenantId
            preferences[ROLE] = session.role.name
            session.outletId?.let { preferences[OUTLET_ID] = it } ?: preferences.remove(OUTLET_ID)
            preferences[ENCRYPTED_ACCESS_TOKEN] = encryptedAccessToken
            preferences[ENCRYPTED_REFRESH_TOKEN] = encryptedRefreshToken
            preferences.remove(ACCESS_TOKEN)
            preferences.remove(REFRESH_TOKEN)
            preferences[EXPIRES_AT] = session.expiresAt
            preferences[DEVICE_ID] = session.deviceId
        }
    }

    suspend fun getSession(): AuthSession? = sessionFlow.first()

    suspend fun getAccessToken(): String? = getSession()?.accessToken

    suspend fun updateTokenPair(
        expectedIdentity: SessionIdentity,
        expectedRefreshToken: String,
        accessToken: String,
        refreshToken: String,
        expiresAt: Long,
    ): Boolean {
        val encryptedAccessToken = secureTokenStorage.encrypt(accessToken)
        val encryptedRefreshToken = secureTokenStorage.encrypt(refreshToken)
        var updated = false
        dataStore.edit { preferences ->
            if (preferences.sessionIdentity() != expectedIdentity) return@edit

            val currentRefreshToken = decryptRefreshTokenOrInvalidate(preferences) ?: return@edit
            if (currentRefreshToken != expectedRefreshToken) return@edit

            preferences[ENCRYPTED_ACCESS_TOKEN] = encryptedAccessToken
            preferences[ENCRYPTED_REFRESH_TOKEN] = encryptedRefreshToken
            preferences.remove(ACCESS_TOKEN)
            preferences.remove(REFRESH_TOKEN)
            preferences[EXPIRES_AT] = expiresAt
            updated = true
        }
        return updated
    }

    suspend fun clearSession() {
        dataStore.edit(::removeSession)
    }

    suspend fun clearSessionIfMatches(
        expectedIdentity: SessionIdentity,
        expectedRefreshToken: String,
    ): Boolean {
        var cleared = false
        dataStore.edit { preferences ->
            if (preferences.sessionIdentity() != expectedIdentity) return@edit

            val currentRefreshToken = decryptRefreshTokenOrInvalidate(preferences) ?: return@edit
            if (currentRefreshToken != expectedRefreshToken) return@edit

            removeSession(preferences)
            cleared = true
        }
        return cleared
    }

    // Resolve against the latest transaction, never mutate using a stale Flow snapshot.
    private suspend fun resolveSession(): AuthSession? {
        var session: AuthSession? = null
        dataStore.edit { current -> session = normalizeSession(current) }
        return session
    }

    private fun normalizeSession(current: MutablePreferences): AuthSession? {
        try {
            val encryptedAccess = current[ENCRYPTED_ACCESS_TOKEN]
            val encryptedRefresh = current[ENCRYPTED_REFRESH_TOKEN]
            val legacyAccess = current[ACCESS_TOKEN]
            val legacyRefresh = current[REFRESH_TOKEN]
            val session = when {
                encryptedAccess != null && encryptedRefresh != null -> buildSession(
                    current,
                    secureTokenStorage.decrypt(encryptedAccess),
                    secureTokenStorage.decrypt(encryptedRefresh),
                )
                encryptedAccess != null || encryptedRefresh != null -> null
                legacyAccess != null && legacyRefresh != null -> {
                    val legacySession = buildSession(current, legacyAccess, legacyRefresh)
                    if (legacySession != null) {
                        val access = secureTokenStorage.encrypt(legacyAccess)
                        val refresh = secureTokenStorage.encrypt(legacyRefresh)
                        current[ENCRYPTED_ACCESS_TOKEN] = access
                        current[ENCRYPTED_REFRESH_TOKEN] = refresh
                    }
                    legacySession
                }
                else -> null
            }
            if (session == null) removeSession(current)
            else {
                current.remove(ACCESS_TOKEN)
                current.remove(REFRESH_TOKEN)
            }
            return session
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            removeSession(current)
            return null
        }
    }

    private fun decryptRefreshTokenOrInvalidate(preferences: MutablePreferences): String? =
        normalizeSession(preferences)?.refreshToken

    private fun buildSession(
        preferences: Preferences,
        accessToken: String,
        refreshToken: String,
    ): AuthSession? {
        val userId = preferences[USER_ID]?.takeIf(String::isNotBlank) ?: return null
        val userName = preferences[USER_NAME]?.takeIf(String::isNotBlank) ?: return null
        val tenantId = preferences[TENANT_ID]?.takeIf(String::isNotBlank) ?: return null
        val role = preferences[ROLE]?.let { storedRole ->
            UserRole.entries.firstOrNull { it.name.equals(storedRole, ignoreCase = true) }
        } ?: return null
        val expiresAt = preferences[EXPIRES_AT] ?: return null
        val deviceId = preferences[DEVICE_ID]?.takeIf(String::isNotBlank) ?: return null
        if (accessToken.isBlank() || refreshToken.isBlank()) return null

        return AuthSession(
            userId = userId,
            userName = userName,
            tenantId = tenantId,
            role = role,
            outletId = preferences[OUTLET_ID]?.takeIf(String::isNotBlank),
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = expiresAt,
            deviceId = deviceId,
        )
    }

    private fun removeSession(preferences: MutablePreferences) {
        preferences.remove(USER_ID)
        preferences.remove(USER_NAME)
        preferences.remove(TENANT_ID)
        preferences.remove(ROLE)
        preferences.remove(OUTLET_ID)
        preferences.remove(ACCESS_TOKEN)
        preferences.remove(REFRESH_TOKEN)
        preferences.remove(ENCRYPTED_ACCESS_TOKEN)
        preferences.remove(ENCRYPTED_REFRESH_TOKEN)
        preferences.remove(EXPIRES_AT)
    }

    internal suspend fun getStoredDeviceId(): String? =
        dataStore.data.first()[DEVICE_ID]?.takeIf(String::isNotBlank)

    internal suspend fun saveDeviceIdIfAbsent(deviceId: String): String {
        var resolvedDeviceId = deviceId
        dataStore.edit { preferences ->
            resolvedDeviceId = preferences[DEVICE_ID]
                ?.takeIf(String::isNotBlank)
                ?: deviceId.also { preferences[DEVICE_ID] = it }
        }
        return resolvedDeviceId
    }

    private fun Preferences.sessionIdentity(): SessionIdentity? {
        val tenantId = this[TENANT_ID]?.takeIf(String::isNotBlank) ?: return null
        val userId = this[USER_ID]?.takeIf(String::isNotBlank) ?: return null
        return SessionIdentity(tenantId = tenantId, userId = userId)
    }

    private fun Preferences.hasSessionMetadata(): Boolean =
        this[USER_ID] != null || this[USER_NAME] != null || this[TENANT_ID] != null ||
            this[ROLE] != null || this[OUTLET_ID] != null || this[EXPIRES_AT] != null

    internal companion object {
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val TENANT_ID = stringPreferencesKey("tenant_id")
        val ROLE = stringPreferencesKey("role")
        val OUTLET_ID = stringPreferencesKey("outlet_id")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val EXPIRES_AT = longPreferencesKey("expires_at")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val ENCRYPTED_ACCESS_TOKEN = stringPreferencesKey("encrypted_access_token")
        val ENCRYPTED_REFRESH_TOKEN = stringPreferencesKey("encrypted_refresh_token")
    }
}
