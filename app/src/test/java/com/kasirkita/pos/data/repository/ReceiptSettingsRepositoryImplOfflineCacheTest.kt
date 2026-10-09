package com.kasirkita.pos.data.repository

import com.kasirkita.pos.core.datastore.InMemoryPreferencesDataStore
import com.kasirkita.pos.core.datastore.ReceiptSettingsDataStore
import com.kasirkita.pos.data.api.ReceiptSettingsApi
import com.kasirkita.pos.data.model.ReceiptSettingsResponse
import com.kasirkita.pos.data.model.UpdateReceiptSettingsRequest
import com.kasirkita.pos.domain.error.ReceiptSettingsError
import com.kasirkita.pos.domain.model.ReceiptFooterSettings
import com.kasirkita.pos.domain.model.ReceiptHeaderSettings
import com.kasirkita.pos.domain.model.ReceiptSettings
import com.kasirkita.pos.domain.model.ReceiptVisibilitySettings
import com.kasirkita.pos.domain.repository.ReceiptSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class ReceiptSettingsRepositoryImplOfflineCacheTest {

    private lateinit var api: FakeReceiptSettingsApi
    private lateinit var dataStore: FakeReceiptSettingsDataStore
    private lateinit var repository: ReceiptSettingsRepositoryImpl

    @Before
    fun setUp() {
        api = FakeReceiptSettingsApi()
        dataStore = FakeReceiptSettingsDataStore()
        repository = ReceiptSettingsRepositoryImpl(api, dataStore)
    }

    @Test
    fun matchingExactKeyCacheReturnedForConnectivityFailure() = runTest {
        val cached = makeSettings("tenant", "outlet", "Cached Store")
        dataStore.putLoadReturn("tenant", "outlet", cached)
        api.setGetFailure(IOException("offline"))

        val result = repository.refreshSettings("tenant", "outlet")

        assertTrue(result.isSuccess)
        assertEquals(cached, result.getOrThrow())
    }

    @Test
    fun matchingExactKeyCacheReturnedForHttp5xx() = runTest {
        val cached = makeSettings("tenant", "outlet", "Cached Store")
        dataStore.putLoadReturn("tenant", "outlet", cached)
        api.setGetResponse(Response.error(503, "".toResponseBody("application/json".toMediaType())))

        val result = repository.refreshSettings("tenant", "outlet")

        assertTrue(result.isSuccess)
        assertEquals(cached, result.getOrThrow())
    }

    @Test
    fun noCacheProducesLegacyFallback() = runTest {
        dataStore.putLoadReturn("tenant", "outlet", null)
        api.setGetFailure(IOException("offline"))

        val result = repository.refreshSettings("tenant", "outlet")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ReceiptSettingsError)
    }

    @Test
    fun anotherOutletsCacheIsNeverUsed() = runTest {
        val cachedWrong = makeSettings("tenant", "other-outlet", "Wrong Outlet")
        dataStore.putLoadReturn("tenant", "other-outlet", cachedWrong)
        api.setGetFailure(IOException("offline"))

        val result = repository.refreshSettings("tenant", "outlet")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ReceiptSettingsError)
    }

    @Test
    fun http4xxDoesNotUseCache() = runTest {
        val cached = makeSettings("tenant", "outlet", "Cached Store")
        dataStore.putLoadReturn("tenant", "outlet", cached)
        api.setGetResponse(Response.error(404, "".toResponseBody("application/json".toMediaType())))

        val result = repository.refreshSettings("tenant", "outlet")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ReceiptSettingsError)
    }

    @Test
    fun authorizationErrorDoesNotUseCache() = runTest {
        val cached = makeSettings("tenant", "outlet", "Cached Store")
        dataStore.putLoadReturn("tenant", "outlet", cached)
        api.setGetResponse(Response.error(401, "".toResponseBody("application/json".toMediaType())))

        val result = repository.refreshSettings("tenant", "outlet")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ReceiptSettingsError)
    }

    @Test
    fun contractMismatchDoesNotUseCache() = runTest {
        val cached = makeSettings("tenant", "outlet", "Cached Store")
        dataStore.putLoadReturn("tenant", "outlet", cached)
        api.setGetResponse(Response.success(makeResponseDto("other-tenant", "other-outlet", "Mismatch")))

        val result = repository.refreshSettings("tenant", "outlet")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ReceiptSettingsError)
    }

    @Test
    fun cancellationPropagates() = runTest {
        api.setGetFailure(CancellationException("cancelled"))

        val result = runCatching { repository.refreshSettings("tenant", "outlet") }

        assertTrue(result.exceptionOrNull() is CancellationException)
    }

    private fun makeSettings(
        tenantId: String,
        outletId: String,
        storeName: String,
    ): ReceiptSettings = ReceiptSettings(
        tenantId = tenantId,
        outletId = outletId,
        header = ReceiptHeaderSettings(storeName = storeName, outletName = "Outlet", address = null, phone = null, additionalText = null),
        visibility = ReceiptVisibilitySettings(true, true, true, true, true),
        footer = ReceiptFooterSettings("Thanks", null),
        templateVersion = 1,
        createdAt = null,
        updatedAt = null,
    )

    private fun makeResponseDto(
        tenantId: String,
        outletId: String,
        storeName: String,
    ): ReceiptSettingsResponse = ReceiptSettingsResponse(
        tenantId = tenantId,
        outletId = outletId,
        headerStoreName = storeName,
        headerOutletName = "Outlet",
        headerAddress = null,
        headerPhone = null,
        headerAdditionalText = null,
        showSku = true,
        showModifiers = true,
        showItemNotes = true,
        showCashier = true,
        showCustomer = true,
        footerThankYouText = "Thanks",
        footerPromoText = null,
        templateVersion = 1,
        createdAt = null,
        updatedAt = null,
    )

    // Copied from ReceiptSettingsRepositoryImplTest
    private class FakeReceiptSettingsApi : ReceiptSettingsApi {
        private var getResponse: Response<ReceiptSettingsResponse> = Response.success(
            ReceiptSettingsResponse(
                tenantId = "default",
                outletId = "default",
                headerStoreName = "Default",
                headerOutletName = "Default",
                headerAddress = null,
                headerPhone = null,
                headerAdditionalText = null,
                showSku = false,
                showModifiers = false,
                showItemNotes = false,
                showCashier = false,
                showCustomer = false,
                footerThankYouText = "Thanks",
                footerPromoText = null,
                templateVersion = 1,
                createdAt = null,
                updatedAt = null,
            ),
        )
        private var getFailure: Throwable? = null

        fun setGetResponse(response: Response<ReceiptSettingsResponse>) {
            getResponse = response
            getFailure = null
        }

        fun setGetFailure(failure: Throwable) {
            getFailure = failure
        }

        override suspend fun getReceiptSettings(outletId: String): Response<ReceiptSettingsResponse> {
            getFailure?.let { throw it }
            return getResponse
        }

        override suspend fun putReceiptSettings(outletId: String, request: UpdateReceiptSettingsRequest): Response<ReceiptSettingsResponse> {
            return getResponse
        }
    }

    private class FakeReceiptSettingsDataStore : ReceiptSettingsDataStore(InMemoryPreferencesDataStore()) {
        private val store = mutableMapOf<Pair<String, String>, ReceiptSettings>()

        fun putLoadReturn(tenantId: String, outletId: String, settings: ReceiptSettings?) {
            if (settings == null) store.remove(tenantId to outletId) else store[tenantId to outletId] = settings
        }

        override suspend fun loadSettings(tenantId: String, outletId: String): ReceiptSettings? {
            return store[tenantId to outletId]
        }

        override suspend fun saveSettings(tenantId: String, outletId: String, settings: ReceiptSettings) {
            store[tenantId to outletId] = settings
        }
    }
}