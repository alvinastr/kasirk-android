package com.kasirkita.pos.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kasirkita.pos.core.network.AuthTokenProvider
import com.kasirkita.pos.domain.model.UserSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "auth_session",
)

@Singleton
class TokenDataStore @Inject constructor(
    @ApplicationContext context: Context,
) : AuthTokenProvider {

    private val dataStore = context.tokenDataStore

    suspend fun saveSession(session: UserSession) {
        dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN] = session.accessToken
            preferences[TENANT_ID] = session.tenantId
            preferences[USER_ID] = session.userId
            preferences[ROLE] = session.role.name
        }
    }

    override suspend fun getToken(): String? = dataStore.data.first()[ACCESS_TOKEN]

    suspend fun clearSession() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    private companion object {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val TENANT_ID = stringPreferencesKey("tenant_id")
        val USER_ID = stringPreferencesKey("user_id")
        val ROLE = stringPreferencesKey("role")
    }
}
