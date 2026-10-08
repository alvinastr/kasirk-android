package com.kasirkita.pos.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import com.kasirkita.pos.domain.model.ReceiptSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.receiptSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "receipt_settings",
    corruptionHandler = ReplaceFileCorruptionHandler(
        produceNewData = { emptyPreferences() },
    ),
)

/**
 * Persistent cache for receipt settings, isolated by (tenantId, outletId).
 *
 * Storage choice: DataStore Preferences, not Room.
 *   - Receipt settings are a single per-(tenant, outlet) document of scalar
 *     fields, not relational data, so a Room entity would buy nothing.
 *   - Room would require a schema migration, which the spec forbids doing on
 *     assumption. DataStore needs none: settings serialize to one JSON string.
 *   - The app already owns this exact pattern for `OperationalContextDataStore`
 *     and `AuthSessionDataStore`, including the same corruption handling.
 *   - Preference keys are namespaced per composite key
 *     (`settings_<tenantId>:<outletId>`), matching how the operational-context
 *     store scopes its keys, so no unbounded key namespace is introduced.
 *
 * Guarantees:
 *   - `saveSettings` writes through `DataStore.edit`, a single atomic
 *     transform, so concurrent writes cannot interleave partial state.
 *   - Only one key is touched per save, so replacing one tenant/outlet entry
 *     never disturbs another.
 *   - This DataStore file is dedicated exclusively to Receipt Settings entries.
 *     `ReplaceFileCorruptionHandler` handles file-level corruption by resetting
 *     this dedicated file; malformed JSON in one valid preference entry yields
 *     null for that key only and does not affect other entries.
 *   - Cancellation propagates; no failure is swallowed into a cache hit.
 */
@Singleton
open class ReceiptSettingsDataStore internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.receiptSettingsDataStore)

    private val gson: Gson = GsonBuilder().create()

    /** Writes [settings] as the single source of truth for the tenant/outlet pair. */
    open suspend fun saveSettings(tenantId: String, outletId: String, settings: ReceiptSettings) {
        val key = settingsKey(tenantId, outletId)
        val json = gson.toJson(settings)
        dataStore.edit { preferences -> preferences[key] = json }
    }

    /**
     * Reads the cached settings for the pair, or null when nothing valid is
     * stored. Malformed JSON in this entry is treated as absent rather than as a crash.
     */
    open suspend fun loadSettings(tenantId: String, outletId: String): ReceiptSettings? {
        val key = settingsKey(tenantId, outletId)
        val json = dataStore.data.first()[key] ?: return null
        return decodeSettings(json, tenantId, outletId)
    }

    /** Observes the persisted value for one exact tenant/outlet key. */
    open fun observeSettings(tenantId: String, outletId: String): Flow<ReceiptSettings?> {
        val key = settingsKey(tenantId, outletId)
        return dataStore.data
            .map { preferences ->
                preferences[key]?.let { json -> decodeSettings(json, tenantId, outletId) }
            }
            .distinctUntilChanged()
    }

    private fun decodeSettings(json: String, tenantId: String, outletId: String): ReceiptSettings? = try {
        gson.fromJson(json, ReceiptSettings::class.java)
            ?.takeIf { it.tenantId == tenantId && it.outletId == outletId }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: JsonSyntaxException) {
        null
    } catch (_: JsonParseException) {
        null
    } catch (_: IllegalStateException) {
        null
    }

    private fun settingsKey(
        tenantId: String,
        outletId: String,
    ): Preferences.Key<String> = stringPreferencesKey("settings_$tenantId:$outletId")
}