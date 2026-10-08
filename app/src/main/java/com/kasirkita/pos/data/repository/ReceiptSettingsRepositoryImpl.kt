package com.kasirkita.pos.data.repository

import com.kasirkita.pos.core.datastore.ReceiptSettingsDataStore
import com.kasirkita.pos.data.api.ReceiptSettingsApi
import com.kasirkita.pos.data.model.ReceiptSettingsResponse
import com.kasirkita.pos.data.model.UpdateReceiptSettingsRequest
import com.kasirkita.pos.data.model.toData
import com.kasirkita.pos.data.model.toDomain
import com.kasirkita.pos.domain.error.ReceiptSettingsError
import com.kasirkita.pos.domain.model.ReceiptSettings
import com.kasirkita.pos.domain.model.ReceiptSettingsUpdate
import com.kasirkita.pos.domain.repository.ReceiptSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for receipt settings, caching effective settings per
 * (tenantId, outletId).
 *
 * Read fallback policy:
 *   - Network connectivity failure (IOException) may return the last-known
 *     matching cache entry.
 *   - HTTP 5xx may return the matching cache entry.
 *   - HTTP 400/401/403/404 and response-contract/JSON parsing failures always
 *     propagate and never silently return cached data.
 *
 * Write policy (M17B): no offline write queue. The cache is replaced only after
 * the server confirms success, and it stores the canonical server response
 * rather than the outgoing request.
 */
@Singleton
class ReceiptSettingsRepositoryImpl @Inject constructor(
    private val api: ReceiptSettingsApi,
    private val dataStore: ReceiptSettingsDataStore,
) : ReceiptSettingsRepository {

    override suspend fun getCachedSettings(tenantId: String, outletId: String): Result<ReceiptSettings> =
        resultCatching {
            loadCachedSettings(tenantId, outletId)
                ?: throw ReceiptSettingsError(0, "CACHE_MISS", "No cached settings for $tenantId/$outletId")
        }

    override fun observeCachedSettings(tenantId: String, outletId: String): Flow<ReceiptSettings?> =
        dataStore.observeSettings(tenantId, outletId)

    override suspend fun refreshSettings(tenantId: String, outletId: String): Result<ReceiptSettings> =
        resultCatching {
            val settings = try {
                api.getReceiptSettings(outletId).bodyOrThrow()
                    .toDomain()
                    .validatedIdentity(tenantId, outletId)
            } catch (error: ReceiptSettingsError) {
                if (error.httpCode.isServerError()) return@resultCatching fallback(tenantId, outletId, error)
                throw error
            } catch (error: IOException) {
                return@resultCatching fallback(tenantId, outletId, error)
            }
            replaceCache(tenantId, outletId, settings)
            settings
        }

    override suspend fun updateSettings(
        tenantId: String,
        outletId: String,
        update: ReceiptSettingsUpdate,
    ): Result<ReceiptSettings> = resultCatching {
        val updated = api.putReceiptSettings(outletId, update.toData())
            .bodyOrThrow()
            .toDomain()
            .validatedIdentity(tenantId, outletId)
        replaceCache(tenantId, outletId, updated)
        updated
    }

    /**
     * Atomically replaces the single cache entry for the tenant/outlet pair,
     * leaving every other pair untouched.
     */
    private suspend fun replaceCache(tenantId: String, outletId: String, settings: ReceiptSettings) {
        dataStore.saveSettings(tenantId, outletId, settings)
    }

    private suspend fun loadCachedSettings(tenantId: String, outletId: String): ReceiptSettings? =
        dataStore.loadSettings(tenantId, outletId)

    private suspend fun fallback(
        tenantId: String,
        outletId: String,
        cause: Throwable,
    ): ReceiptSettings = loadCachedSettings(tenantId, outletId)
        ?: throw ReceiptSettingsError(
            httpCode = (cause as? ReceiptSettingsError)?.httpCode ?: 0,
            errorCode = (cause as? ReceiptSettingsError)?.errorCode
                ?: if (cause is IOException) "NETWORK_UNAVAILABLE" else null,
            message = "No cached receipt settings available for $tenantId/$outletId: ${cause.message}",
        )

    private fun ReceiptSettings.validatedIdentity(tenantId: String, outletId: String): ReceiptSettings {
        if (this.tenantId == tenantId && this.outletId == outletId) return this
        throw ReceiptSettingsError(
            httpCode = 0,
            errorCode = "CONTRACT_MISMATCH",
            message = "Receipt settings response identity mismatch for $tenantId/$outletId",
        )
    }

    /**
     * Like [runCatching], but rethrows cancellation so a cancelled coroutine can
     * never be reported to callers as a cache fallback success.
     */
    private inline fun <T> resultCatching(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }

    private fun Int.isServerError(): Boolean = this in 500..599

    /**
     * Unwraps a Retrofit response, mapping transport failures onto the
     * domain error so callers can branch on [ReceiptSettingsError.httpCode].
     */
    private fun <T> Response<T>.bodyOrThrow(): T {
        if (isSuccessful) {
            return body() ?: throw ReceiptSettingsError(code(), null, "Empty response body")
        }
        val raw = errorBody()?.string()
        var errorCode: String? = null
        var message: String? = null
        if (!raw.isNullOrBlank()) {
            runCatching {
                val json = com.google.gson.JsonParser.parseString(raw).asJsonObject
                errorCode = json.get("error_code")?.takeUnless { it.isJsonNull }?.asString
                message = json.get("message")?.let { value ->
                    if (value.isJsonArray) value.asJsonArray.joinToString(", ") { it.asString }
                    else if (!value.isJsonNull) value.asString else null
                }
            }
        }
        throw ReceiptSettingsError(code(), errorCode, message ?: message())
    }
}