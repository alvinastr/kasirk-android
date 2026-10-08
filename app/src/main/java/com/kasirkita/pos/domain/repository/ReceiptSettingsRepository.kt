package com.kasirkita.pos.domain.repository

import com.kasirkita.pos.domain.model.ReceiptSettings
import com.kasirkita.pos.domain.model.ReceiptSettingsUpdate
import kotlinx.coroutines.flow.Flow

interface ReceiptSettingsRepository {

    /**
     * Reads the last-known settings for the tenant/outlet pair.
     *
     * Never touches the network. Returns failure when no cache entry exists,
     * so callers can synthesize legacy defaults instead of guessing.
     */
    suspend fun getCachedSettings(tenantId: String, outletId: String): Result<ReceiptSettings>

    /**
     * Observes the cached settings for the tenant/outlet pair, emitting null
     * when no cache entry exists.
     */
    fun observeCachedSettings(tenantId: String, outletId: String): Flow<ReceiptSettings?>

    /**
     * Fetches effective settings from the server and refreshes the cache.
     *
     * Falls back to the matching cache entry on network connectivity failure and
     * on HTTP 5xx. HTTP 400/401/403/404 and response-contract/JSON parsing
     * failures always propagate and never silently return cached data.
     */
    suspend fun refreshSettings(tenantId: String, outletId: String): Result<ReceiptSettings>

    /**
     * Fully replaces the settings online and replaces the cache with the
     * canonical server response. A failed PUT leaves the previous cache intact.
     */
    suspend fun updateSettings(
        tenantId: String,
        outletId: String,
        update: ReceiptSettingsUpdate,
    ): Result<ReceiptSettings>
}