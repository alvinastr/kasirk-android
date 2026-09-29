package com.kasirkita.pos.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kasirkita.pos.domain.model.AuthSession
import com.kasirkita.pos.domain.model.UserRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authV2SessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "auth_v2_session",
)

@Singleton
class AuthSessionDataStore internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.authV2SessionDataStore)

    val sessionFlow: Flow<AuthSession?> = dataStore.data
        .map(::toSession)
        .distinctUntilChanged()

    suspend fun saveSession(session: AuthSession) {
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
            session.outletId?.let { outletId ->
                preferences[OUTLET_ID] = outletId
            } ?: preferences.remove(OUTLET_ID)
            preferences[ACCESS_TOKEN] = session.accessToken
            preferences[REFRESH_TOKEN] = session.refreshToken
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
        var updated = false
        dataStore.edit { preferences ->
            if (
                preferences.sessionIdentity() != expectedIdentity ||
                preferences[REFRESH_TOKEN] != expectedRefreshToken
            ) {
                return@edit
            }

            preferences[ACCESS_TOKEN] = accessToken
            preferences[REFRESH_TOKEN] = refreshToken
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
            if (
                preferences.sessionIdentity() != expectedIdentity ||
                preferences[REFRESH_TOKEN] != expectedRefreshToken
            ) {
                return@edit
            }

            removeSession(preferences)
            cleared = true
        }
        return cleared
    }

    private fun toSession(preferences: Preferences): AuthSession? {
        val userId = preferences[USER_ID]?.takeIf(String::isNotBlank) ?: return null
        val userName = preferences[USER_NAME]?.takeIf(String::isNotBlank) ?: return null
        val tenantId = preferences[TENANT_ID]?.takeIf(String::isNotBlank) ?: return null
        val role = preferences[ROLE]
            ?.let { storedRole ->
                UserRole.entries.firstOrNull { role ->
                    role.name.equals(storedRole, ignoreCase = true)
                }
            }
            ?: return null
        val accessToken = preferences[ACCESS_TOKEN]?.takeIf(String::isNotBlank) ?: return null
        val refreshToken = preferences[REFRESH_TOKEN]?.takeIf(String::isNotBlank) ?: return null
        val expiresAt = preferences[EXPIRES_AT] ?: return null
        val deviceId = preferences[DEVICE_ID]?.takeIf(String::isNotBlank) ?: return null

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

    private fun removeSession(preferences: androidx.datastore.preferences.core.MutablePreferences) {
        preferences.remove(USER_ID)
        preferences.remove(USER_NAME)
        preferences.remove(TENANT_ID)
        preferences.remove(ROLE)
        preferences.remove(OUTLET_ID)
        preferences.remove(ACCESS_TOKEN)
        preferences.remove(REFRESH_TOKEN)
        preferences.remove(EXPIRES_AT)
    }

    internal suspend fun getStoredDeviceId(): String? =
        dataStore.data.first()[DEVICE_ID]?.takeIf(String::isNotBlank)

    internal suspend fun saveDeviceIdIfAbsent(deviceId: String): String {
        var resolvedDeviceId = deviceId
        dataStore.edit { preferences ->
            resolvedDeviceId = preferences[DEVICE_ID]
                ?.takeIf(String::isNotBlank)
                ?: deviceId.also { newDeviceId ->
                    preferences[DEVICE_ID] = newDeviceId
                }
        }
        return resolvedDeviceId
    }

    private fun Preferences.sessionIdentity(): SessionIdentity? {
        val tenantId = this[TENANT_ID]?.takeIf(String::isNotBlank) ?: return null
        val userId = this[USER_ID]?.takeIf(String::isNotBlank) ?: return null
        return SessionIdentity(tenantId = tenantId, userId = userId)
    }

    private companion object {
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val TENANT_ID = stringPreferencesKey("tenant_id")
        val ROLE = stringPreferencesKey("role")
        val OUTLET_ID = stringPreferencesKey("outlet_id")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val EXPIRES_AT = longPreferencesKey("expires_at")
        val DEVICE_ID = stringPreferencesKey("device_id")
    }
}
