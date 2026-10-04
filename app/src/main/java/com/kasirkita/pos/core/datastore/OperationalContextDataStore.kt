package com.kasirkita.pos.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.domain.model.Shift
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.operationalContextDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "operational_context",
    corruptionHandler = ReplaceFileCorruptionHandler(
        produceNewData = { emptyPreferences() },
    ),
)

@Singleton
class OperationalContextDataStore internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.operationalContextDataStore)

    suspend fun saveOutlet(tenantId: String, userId: String, outlet: Outlet) {
        withContext(NonCancellable) {
            try {
                dataStore.edit { preferences ->
                    preferences[outletKey(tenantId, userId, "id")] = outlet.id
                    preferences[outletKey(tenantId, userId, "tenant_id")] = outlet.tenantId
                    preferences[outletKey(tenantId, userId, "name")] = outlet.name
                    outlet.address?.let { preferences[outletKey(tenantId, userId, "address")] = it }
                        ?: preferences.remove(outletKey(tenantId, userId, "address"))
                    preferences[outletBooleanKey(tenantId, userId, "is_active")] = outlet.isActive
                    preferences[outletKey(tenantId, userId, "created_at")] = outlet.createdAt
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                Unit
            }
        }
    }

    suspend fun getOutlet(tenantId: String, userId: String): Outlet? = try {
        val preferences = dataStore.data.first()
        val id = preferences[outletKey(tenantId, userId, "id")] ?: return null
        val outletTenantId = preferences[outletKey(tenantId, userId, "tenant_id")] ?: return null
        val name = preferences[outletKey(tenantId, userId, "name")] ?: return null
        val address = preferences[outletKey(tenantId, userId, "address")]
        val isActive = preferences[outletBooleanKey(tenantId, userId, "is_active")] ?: return null
        val createdAt = preferences[outletKey(tenantId, userId, "created_at")] ?: return null

        Outlet(
            id = id,
            tenantId = outletTenantId,
            name = name,
            address = address,
            isActive = isActive,
            createdAt = createdAt,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        null
    }

    suspend fun clearOutlet(tenantId: String, userId: String) {
        withContext(NonCancellable) {
            try {
                dataStore.edit { preferences ->
                    preferences.remove(outletKey(tenantId, userId, "id"))
                    preferences.remove(outletKey(tenantId, userId, "tenant_id"))
                    preferences.remove(outletKey(tenantId, userId, "name"))
                    preferences.remove(outletKey(tenantId, userId, "address"))
                    preferences.remove(outletBooleanKey(tenantId, userId, "is_active"))
                    preferences.remove(outletKey(tenantId, userId, "created_at"))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                Unit
            }
        }
    }

    suspend fun saveShift(tenantId: String, userId: String, shift: Shift) {
        withContext(NonCancellable) {
            try {
                dataStore.edit { preferences ->
                    preferences[shiftKey(tenantId, userId, "id")] = shift.id
                    preferences[shiftKey(tenantId, userId, "outlet_id")] = shift.outletId
                    preferences[shiftKey(tenantId, userId, "user_id")] = shift.userId
                    val openingCashKey = shiftLongKey(tenantId, userId, "opening_cash")
                    shift.openingCash
                        ?.let { preferences[openingCashKey] = it }
                        ?: preferences.remove(openingCashKey)
                    preferences[shiftKey(tenantId, userId, "status")] = shift.status
                    preferences[shiftKey(tenantId, userId, "opened_at")] = shift.openedAt
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                Unit
            }
        }
    }

    suspend fun getShift(tenantId: String, userId: String): Shift? = try {
        val preferences = dataStore.data.first()
        val id = preferences[shiftKey(tenantId, userId, "id")] ?: return null
        val outletId = preferences[shiftKey(tenantId, userId, "outlet_id")] ?: return null
        val shiftUserId = preferences[shiftKey(tenantId, userId, "user_id")] ?: return null
        val openingCash = preferences[shiftLongKey(tenantId, userId, "opening_cash")]
        val status = preferences[shiftKey(tenantId, userId, "status")] ?: return null
        val openedAt = preferences[shiftKey(tenantId, userId, "opened_at")] ?: return null

        Shift(
            id = id,
            outletId = outletId,
            userId = shiftUserId,
            openingCash = openingCash,
            closingCash = null,
            expectedCash = null,
            difference = null,
            status = status,
            openedAt = openedAt,
            closedAt = null,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        null
    }

    suspend fun clearShift(tenantId: String, userId: String) {
        withContext(NonCancellable) {
            try {
                dataStore.edit { preferences ->
                    preferences.remove(shiftKey(tenantId, userId, "id"))
                    preferences.remove(shiftKey(tenantId, userId, "outlet_id"))
                    preferences.remove(shiftKey(tenantId, userId, "user_id"))
                    preferences.remove(shiftLongKey(tenantId, userId, "opening_cash"))
                    preferences.remove(shiftKey(tenantId, userId, "status"))
                    preferences.remove(shiftKey(tenantId, userId, "opened_at"))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                Unit
            }
        }
    }

    private fun outletKey(tenantId: String, userId: String, field: String): Preferences.Key<String> =
        stringPreferencesKey("outlet_${tenantId}_${userId}_$field")

    private fun outletBooleanKey(tenantId: String, userId: String, field: String): Preferences.Key<Boolean> =
        booleanPreferencesKey("outlet_${tenantId}_${userId}_$field")

    private fun shiftKey(tenantId: String, userId: String, field: String): Preferences.Key<String> =
        stringPreferencesKey("shift_${tenantId}_${userId}_$field")

    private fun shiftLongKey(tenantId: String, userId: String, field: String): Preferences.Key<Long> =
        longPreferencesKey("shift_${tenantId}_${userId}_$field")
}
