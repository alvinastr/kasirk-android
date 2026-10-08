package com.kasirkita.pos.data.repository

import com.kasirkita.pos.core.datastore.InMemoryPreferencesDataStore
import com.kasirkita.pos.core.datastore.ReceiptSettingsDataStore
import com.kasirkita.pos.data.api.ReceiptSettingsApi
import com.kasirkita.pos.data.model.ReceiptSettingsResponse
import com.kasirkita.pos.data.model.UpdateReceiptSettingsRequest
import com.kasirkita.pos.data.model.toDomain
import com.kasirkita.pos.domain.error.ReceiptSettingsError
import com.kasirkita.pos.domain.model.ReceiptSettings
import com.kasirkita.pos.domain.model.ReceiptSettingsUpdate
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class ReceiptSettingsRepositoryImplTest {

    private val testTenantId = "test_tenant"
    private val testOutletId = "test_outlet"

    @Test
    fun refreshSettings_success_updatesCache() = runBlocking {
        val api = FakeReceiptSettingsApi()
        val dataStore = FakeReceiptSettingsDataStore()
        val repository = ReceiptSettingsRepositoryImpl(api, dataStore)

        val responseDto = makeResponseDto(
            storeName = "Test Store",
            outletName = "Test Outlet",
            address = "123 Test St",
            phone = "555-1234",
            additionalText = "Additional Info",
            showSku = true,
            showModifiers = false,
            showItemNotes = true,
            showCashier = true,
            showCustomer = false,
            footerThankYouText = "Thank you!",
            footerPromoText = "Promo",
            templateVersion = 1,
            createdAt = "2023-01-01T00:00:00Z",
            updatedAt = "2023-01-02T00:00:00Z",
        )
        api.setGetResponse(Response.success(responseDto))

        val result = repository.refreshSettings(testTenantId, testOutletId)

        assertTrue(result.isSuccess)
        val settings = result.getOrNull()
        assertNotNull(settings)
        assertSettingsCorrect(settings, testTenantId, testOutletId, responseDto)
        assertTrue(dataStore.saveCalled)
        assertEquals(settings, dataStore.lastSettings)
    }

    @Test
    fun refreshSettings_networkFailure_fallsBackToCache() = runBlocking {
        val api = FakeReceiptSettingsApi()
        val dataStore = FakeReceiptSettingsDataStore()
        val repository = ReceiptSettingsRepositoryImpl(api, dataStore)

        val cachedDto = makeResponseDto(
            storeName = "Cached Store",
            outletName = "Cached Outlet",
            address = null,
            phone = null,
            additionalText = null,
            showSku = false,
            showModifiers = true,
            showItemNotes = false,
            showCashier = false,
            showCustomer = true,
            footerThankYouText = "Thanks",
            footerPromoText = null,
            templateVersion = 5,
            createdAt = "2022-01-01T00:00:00Z",
            updatedAt = "2022-01-02T00:00:00Z",
        )
        dataStore.setLoadReturn(cachedDto.toDomain())

        api.setGetFailure(IOException("Network unreachable"))

        val result = repository.refreshSettings(testTenantId, testOutletId)

        assertTrue(result.isSuccess)
        val settings = result.getOrNull()
        assertNotNull(settings)
        assertCachedSettingsCorrect(settings, cachedDto)
        assertFalse(dataStore.saveCalled)
    }

    @Test
    fun refreshSettings_http5xx_fallsBackToCache() = runBlocking {
        val api = FakeReceiptSettingsApi()
        val dataStore = FakeReceiptSettingsDataStore()
        val repository = ReceiptSettingsRepositoryImpl(api, dataStore)

        val cachedDto = makeResponseDto(
            storeName = "5xx Store",
            outletName = "5xx Outlet",
            address = null,
            phone = null,
            additionalText = null,
            showSku = true,
            showModifiers = true,
            showItemNotes = true,
            showCashier = true,
            showCustomer = true,
            footerThankYouText = "Server Error",
            footerPromoText = null,
            templateVersion = 9,
            createdAt = "2021-01-01T00:00:00Z",
            updatedAt = "2021-01-02T00:00:00Z",
        )
        dataStore.setLoadReturn(cachedDto.toDomain())

        api.setGetResponse(Response.error(500, "{}".toResponseBody("application/json".toMediaType())))

        val result = repository.refreshSettings(testTenantId, testOutletId)

        assertTrue(result.isSuccess)
        val settings = result.getOrNull()
        assertNotNull(settings)
        assertCachedSettingsCorrect(settings, cachedDto)
        assertFalse(dataStore.saveCalled)
    }

    @Test
    fun refreshSettings_http4xx_propagatesAndPreservesOldCache() = runBlocking {
        val api = FakeReceiptSettingsApi()
        val dataStore = FakeReceiptSettingsDataStore()
        val repository = ReceiptSettingsRepositoryImpl(api, dataStore)

        val goodCachedDto = makeResponseDto(
            storeName = "GOOD Store",
            outletName = "GOOD Outlet",
            address = null,
            phone = null,
            additionalText = null,
            showSku = true,
            showModifiers = false,
            showItemNotes = true,
            showCashier = false,
            showCustomer = true,
            footerThankYouText = "Good",
            footerPromoText = null,
            templateVersion = 3,
            createdAt = "2022-06-01T00:00:00Z",
            updatedAt = "2022-06-02T00:00:00Z",
        )
        dataStore.setLoadReturn(goodCachedDto.toDomain())

        api.setGetResponse(Response.error(404, "{}".toResponseBody("application/json".toMediaType())))

        val result = repository.refreshSettings(testTenantId, testOutletId)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is ReceiptSettingsError)
        assertEquals(404, (error as ReceiptSettingsError).httpCode)
        assertFalse(dataStore.saveCalled)
        val cached = dataStore.loadSettings(testTenantId, testOutletId)
        assertNotNull(cached)
        assertEquals(goodCachedDto.toDomain(), cached)
    }

    @Test
    fun updateSettings_success_updatesCache() = runBlocking {
        val api = FakeReceiptSettingsApi()
        val dataStore = FakeReceiptSettingsDataStore()
        val repository = ReceiptSettingsRepositoryImpl(api, dataStore)

        val update = ReceiptSettingsUpdate(
            headerStoreName = "Updated Store",
            headerOutletName = "Updated Outlet",
            headerAddress = "456 Update Ave",
            headerPhone = "555-5678",
            headerAdditionalText = "Updated Info",
            showSku = false,
            showModifiers = true,
            showItemNotes = false,
            showCashier = false,
            showCustomer = true,
            footerThankYouText = "Thanks",
            footerPromoText = "New Promo"
        )
        val responseDto = makeResponseDto(
            storeName = update.headerStoreName,
            outletName = update.headerOutletName,
            address = update.headerAddress,
            phone = update.headerPhone,
            additionalText = update.headerAdditionalText,
            showSku = update.showSku,
            showModifiers = update.showModifiers,
            showItemNotes = update.showItemNotes,
            showCashier = update.showCashier,
            showCustomer = update.showCustomer,
            footerThankYouText = update.footerThankYouText,
            footerPromoText = update.footerPromoText,
            templateVersion = 2,
            createdAt = "2023-01-01T00:00:00Z",
            updatedAt = "2023-01-03T00:00:00Z",
        )
        api.setPutResponse(Response.success(responseDto))

        val result = repository.updateSettings(testTenantId, testOutletId, update)

        assertTrue(result.isSuccess)
        val settings = result.getOrNull()
        assertNotNull(settings)
        assertEquals(update.headerStoreName, settings?.header?.storeName)
        assertEquals(update.headerOutletName, settings?.header?.outletName)
        assertEquals(update.headerAddress, settings?.header?.address)
        assertEquals(update.headerPhone, settings?.header?.phone)
        assertEquals(update.headerAdditionalText, settings?.header?.additionalText)
        assertEquals(update.showSku, settings?.visibility?.showSku)
        assertEquals(update.showModifiers, settings?.visibility?.showModifiers)
        assertEquals(update.showItemNotes, settings?.visibility?.showItemNotes)
        assertEquals(update.showCashier, settings?.visibility?.showCashier)
        assertEquals(update.showCustomer, settings?.visibility?.showCustomer)
        assertEquals(update.footerThankYouText, settings?.footer?.thankYouText)
        assertEquals(update.footerPromoText, settings?.footer?.promoText)
        assertTrue(dataStore.saveCalled)
        assertNotNull(dataStore.lastSettings)
    }

    @Test
    fun updateSettings_http4xx_failsAndPreservesOldCache() = runBlocking {
        val api = FakeReceiptSettingsApi()
        val dataStore = FakeReceiptSettingsDataStore()
        val repository = ReceiptSettingsRepositoryImpl(api, dataStore)

        val goodCachedSettings = makeResponseDto(
            storeName = "GOOD Store",
            outletName = "GOOD Outlet",
            address = null,
            phone = null,
            additionalText = null,
            showSku = true,
            showModifiers = false,
            showItemNotes = true,
            showCashier = false,
            showCustomer = true,
            footerThankYouText = "Good",
            footerPromoText = null,
            templateVersion = 1,
            createdAt = "2022-01-01T00:00:00Z",
            updatedAt = "2022-01-02T00:00:00Z",
        ).toDomain()
        dataStore.setLoadReturn(goodCachedSettings)

        val badUpdate = ReceiptSettingsUpdate(
            headerStoreName = "BAD Store",
            headerOutletName = "BAD Outlet",
            headerAddress = null,
            headerPhone = null,
            headerAdditionalText = null,
            showSku = false,
            showModifiers = false,
            showItemNotes = false,
            showCashier = false,
            showCustomer = false,
            footerThankYouText = "Bad",
            footerPromoText = null,
        )
        api.setPutResponse(Response.error(400, "{}".toResponseBody("application/json".toMediaType())))

        val result = repository.updateSettings(testTenantId, testOutletId, badUpdate)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is ReceiptSettingsError)
        assertEquals(400, (error as ReceiptSettingsError).httpCode)
        assertFalse(dataStore.saveCalled)
        val cached = repository.getCachedSettings(testTenantId, testOutletId).getOrNull()
        assertNotNull(cached)
        assertEquals(goodCachedSettings, cached)
    }

    @Test
    fun getCachedSettings_noCache_returnsFailure() = runBlocking {
        val api = FakeReceiptSettingsApi()
        val dataStore = FakeReceiptSettingsDataStore()
        // dataStore.loadSettings returns null by default
        val repository = ReceiptSettingsRepositoryImpl(api, dataStore)

        val result = repository.getCachedSettings(testTenantId, testOutletId)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is ReceiptSettingsError)
        assertEquals(0, (error as ReceiptSettingsError).httpCode)
        assertEquals("CACHE_MISS", (error as ReceiptSettingsError).errorCode)
    }

    @Test
    fun cache_isolatedByTenantAndOutlet() = runBlocking {
        val api = FakeReceiptSettingsApi()
        val dataStore = ReceiptSettingsDataStore(InMemoryPreferencesDataStore())
        val repository = ReceiptSettingsRepositoryImpl(api, dataStore)

        api.setGetResponse(Response.success(makeResponseDto(tenantId = "tenant-a", outletId = "outlet-1", storeName = "Outlet One")))
        assertTrue(repository.refreshSettings("tenant-a", "outlet-1").isSuccess)

        api.setGetResponse(Response.success(makeResponseDto(tenantId = "tenant-a", outletId = "outlet-2", storeName = "Outlet Two")))
        assertTrue(repository.refreshSettings("tenant-a", "outlet-2").isSuccess)

        assertEquals("Outlet One", repository.getCachedSettings("tenant-a", "outlet-1").getOrNull()?.header?.storeName)
        assertEquals("Outlet Two", repository.getCachedSettings("tenant-a", "outlet-2").getOrNull()?.header?.storeName)
        assertTrue(repository.getCachedSettings("tenant-b", "outlet-1").isFailure)
    }

    @Test
    fun refreshSettings_tenantMismatch_returnsContractMismatchAndPreservesCache() = runBlocking {
        val api = FakeReceiptSettingsApi()
        val backing = InMemoryPreferencesDataStore()
        val dataStore = ReceiptSettingsDataStore(backing)
        val repository = ReceiptSettingsRepositoryImpl(api, dataStore)
        val previous = makeResponseDto(storeName = "Previous").toDomain()
        dataStore.saveSettings(testTenantId, testOutletId, previous)
        api.setGetResponse(Response.success(makeResponseDto(tenantId = "other", storeName = "Wrong")))

        val result = repository.refreshSettings(testTenantId, testOutletId)

        assertEquals("CONTRACT_MISMATCH", (result.exceptionOrNull() as ReceiptSettingsError).errorCode)
        assertEquals(previous, dataStore.loadSettings(testTenantId, testOutletId))
    }

    @Test
    fun refreshSettings_outletMismatch_returnsContractMismatchAndPreservesCache() = runBlocking {
        val api = FakeReceiptSettingsApi()
        val dataStore = ReceiptSettingsDataStore(InMemoryPreferencesDataStore())
        val repository = ReceiptSettingsRepositoryImpl(api, dataStore)
        val previous = makeResponseDto(storeName = "Previous").toDomain()
        dataStore.saveSettings(testTenantId, testOutletId, previous)
        api.setGetResponse(Response.success(makeResponseDto(outletId = "other", storeName = "Wrong")))

        val result = repository.refreshSettings(testTenantId, testOutletId)

        assertEquals("CONTRACT_MISMATCH", (result.exceptionOrNull() as ReceiptSettingsError).errorCode)
        assertEquals(previous, dataStore.loadSettings(testTenantId, testOutletId))
    }

    @Test
    fun updateSettings_identityMismatch_preservesPreviousCache() = runBlocking {
        val api = FakeReceiptSettingsApi()
        val dataStore = ReceiptSettingsDataStore(InMemoryPreferencesDataStore())
        val repository = ReceiptSettingsRepositoryImpl(api, dataStore)
        val previous = makeResponseDto(storeName = "Previous").toDomain()
        dataStore.saveSettings(testTenantId, testOutletId, previous)
        api.setPutResponse(Response.success(makeResponseDto(outletId = "other", storeName = "Wrong")))

        val result = repository.updateSettings(testTenantId, testOutletId, makeUpdate("Requested"))

        assertEquals("CONTRACT_MISMATCH", (result.exceptionOrNull() as ReceiptSettingsError).errorCode)
        assertEquals(previous, dataStore.loadSettings(testTenantId, testOutletId))
    }

    @Test
    fun cachedEmbeddedIdentityMismatch_isCacheMiss() = runBlocking {
        val backing = InMemoryPreferencesDataStore()
        val dataStore = ReceiptSettingsDataStore(backing)
        dataStore.saveSettings(testTenantId, testOutletId, makeResponseDto(tenantId = "other", storeName = "Wrong").toDomain())
        val repository = ReceiptSettingsRepositoryImpl(FakeReceiptSettingsApi(), dataStore)

        val result = repository.getCachedSettings(testTenantId, testOutletId)

        assertEquals("CACHE_MISS", (result.exceptionOrNull() as ReceiptSettingsError).errorCode)
        assertNull(repository.observeCachedSettings(testTenantId, testOutletId).first())
    }

    @Test
    fun malformedJson_isolatedToOneKey() = runBlocking {
        val backing = InMemoryPreferencesDataStore()
        val dataStore = ReceiptSettingsDataStore(backing)
        val valid = makeResponseDto(outletId = "valid", storeName = "Valid").toDomain()
        dataStore.saveSettings(testTenantId, "valid", valid)
        backing.edit { it[stringPreferencesKey("settings_$testTenantId:broken")] = "{not-json" }

        assertNull(dataStore.loadSettings(testTenantId, "broken"))
        assertNull(dataStore.observeSettings(testTenantId, "broken").first())
        assertEquals(valid, dataStore.loadSettings(testTenantId, "valid"))
    }

    @Test
    fun coldStartObservation_emitsPersistedValue() = runBlocking {
        val backing = InMemoryPreferencesDataStore()
        val writer = ReceiptSettingsDataStore(backing)
        val expected = makeResponseDto(storeName = "Persisted").toDomain()
        writer.saveSettings(testTenantId, testOutletId, expected)
        val newRepository = ReceiptSettingsRepositoryImpl(FakeReceiptSettingsApi(), ReceiptSettingsDataStore(backing))

        assertEquals(expected, newRepository.observeCachedSettings(testTenantId, testOutletId).first())
    }

    @Test
    fun repositoryObservesWriteFromAnotherInstance() = runBlocking {
        val backing = InMemoryPreferencesDataStore()
        val repositoryOne = ReceiptSettingsRepositoryImpl(FakeReceiptSettingsApi(), ReceiptSettingsDataStore(backing))
        val repositoryTwo = ReceiptSettingsRepositoryImpl(FakeReceiptSettingsApi(), ReceiptSettingsDataStore(backing))
        val expected = makeResponseDto(storeName = "Shared").toDomain()

        ReceiptSettingsDataStore(backing).saveSettings(testTenantId, testOutletId, expected)

        assertEquals(expected, repositoryOne.observeCachedSettings(testTenantId, testOutletId).first())
        assertEquals(expected, repositoryTwo.observeCachedSettings(testTenantId, testOutletId).first())
    }

    @Test
    fun concurrentDifferentKeyWrites_preserveBothDocuments() = runBlocking {
        val dataStore = ReceiptSettingsDataStore(InMemoryPreferencesDataStore())
        val first = makeResponseDto(outletId = "one", storeName = "One").toDomain()
        val second = makeResponseDto(outletId = "two", storeName = "Two").toDomain()

        coroutineScope {
            val a = async { dataStore.saveSettings(testTenantId, "one", first) }
            val b = async { dataStore.saveSettings(testTenantId, "two", second) }
            a.await(); b.await()
        }

        assertEquals(first, dataStore.loadSettings(testTenantId, "one"))
        assertEquals(second, dataStore.loadSettings(testTenantId, "two"))
    }

    @Test
    fun concurrentSameKeyWrites_storeOneCompleteDocument() = runBlocking {
        val dataStore = ReceiptSettingsDataStore(InMemoryPreferencesDataStore())
        val first = makeResponseDto(storeName = "A", outletName = "A", templateVersion = 1).toDomain()
        val second = makeResponseDto(storeName = "B", outletName = "B", templateVersion = 2).toDomain()

        coroutineScope {
            val a = async { dataStore.saveSettings(testTenantId, testOutletId, first) }
            val b = async { dataStore.saveSettings(testTenantId, testOutletId, second) }
            a.await(); b.await()
        }

        assertTrue(dataStore.loadSettings(testTenantId, testOutletId) in setOf(first, second))
    }

    @Test
    fun sequentialWrites_returnLaterCompletedDocument() = runBlocking {
        val dataStore = ReceiptSettingsDataStore(InMemoryPreferencesDataStore())
        val first = makeResponseDto(storeName = "A").toDomain()
        val second = makeResponseDto(storeName = "B", templateVersion = 2).toDomain()
        dataStore.saveSettings(testTenantId, testOutletId, first)
        dataStore.saveSettings(testTenantId, testOutletId, second)

        assertEquals(second, dataStore.loadSettings(testTenantId, testOutletId))
    }

    @Test
    fun refreshSettings_cancellationPropagates() = runBlocking {
        val api = FakeReceiptSettingsApi()
        val dataStore = FakeReceiptSettingsDataStore()
        val repository = ReceiptSettingsRepositoryImpl(api, dataStore)
        api.setGetFailure(CancellationException("cancelled"))

        try {
            repository.refreshSettings(testTenantId, testOutletId)
            throw AssertionError("Expected CancellationException")
        } catch (expected: CancellationException) {
            assertEquals("cancelled", expected.message)
        }
    }

    // --- Helper methods ---

    private fun makeResponseDto(
        tenantId: String = testTenantId,
        outletId: String = testOutletId,
        storeName: String,
        outletName: String = "Test Outlet",
        address: String? = null,
        phone: String? = null,
        additionalText: String? = null,
        showSku: Boolean = true,
        showModifiers: Boolean = false,
        showItemNotes: Boolean = true,
        showCashier: Boolean = true,
        showCustomer: Boolean = false,
        footerThankYouText: String = "Thank you!",
        footerPromoText: String? = null,
        templateVersion: Int = 1,
        createdAt: String? = "2023-01-01T00:00:00Z",
        updatedAt: String? = "2023-01-02T00:00:00Z",
    ): ReceiptSettingsResponse = ReceiptSettingsResponse(
        tenantId = tenantId,
        outletId = outletId,
        headerStoreName = storeName,
        headerOutletName = outletName,
        headerAddress = address,
        headerPhone = phone,
        headerAdditionalText = additionalText,
        showSku = showSku,
        showModifiers = showModifiers,
        showItemNotes = showItemNotes,
        showCashier = showCashier,
        showCustomer = showCustomer,
        footerThankYouText = footerThankYouText,
        footerPromoText = footerPromoText,
        templateVersion = templateVersion,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun assertSettingsCorrect(
        settings: ReceiptSettings?,
        expectedTenantId: String,
        expectedOutletId: String,
        dto: ReceiptSettingsResponse,
    ) {
        assertNotNull(settings)
        assertEquals(expectedTenantId, settings?.tenantId)
        assertEquals(expectedOutletId, settings?.outletId)
        assertEquals(dto.headerStoreName, settings?.header?.storeName)
        assertEquals(dto.headerOutletName, settings?.header?.outletName)
        assertEquals(dto.headerAddress, settings?.header?.address)
        assertEquals(dto.headerPhone, settings?.header?.phone)
        assertEquals(dto.headerAdditionalText, settings?.header?.additionalText)
        assertEquals(dto.showSku, settings?.visibility?.showSku)
        assertEquals(dto.showModifiers, settings?.visibility?.showModifiers)
        assertEquals(dto.showItemNotes, settings?.visibility?.showItemNotes)
        assertEquals(dto.showCashier, settings?.visibility?.showCashier)
        assertEquals(dto.showCustomer, settings?.visibility?.showCustomer)
        assertEquals(dto.footerThankYouText, settings?.footer?.thankYouText)
        assertEquals(dto.footerPromoText, settings?.footer?.promoText)
        assertEquals(dto.templateVersion, settings?.templateVersion)
        assertEquals(dto.createdAt, settings?.createdAt)
        assertEquals(dto.updatedAt, settings?.updatedAt)
    }

    private fun makeUpdate(storeName: String): ReceiptSettingsUpdate = ReceiptSettingsUpdate(
        headerStoreName = storeName,
        headerOutletName = "Outlet",
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
    )

    private fun assertCachedSettingsCorrect(settings: ReceiptSettings?, dto: ReceiptSettingsResponse) {
        assertNotNull(settings)
        assertEquals(dto.headerStoreName, settings?.header?.storeName)
        assertEquals(dto.headerOutletName, settings?.header?.outletName)
        assertEquals(dto.footerThankYouText, settings?.footer?.thankYouText)
        assertEquals(dto.footerPromoText, settings?.footer?.promoText)
        assertEquals(dto.templateVersion, settings?.templateVersion)
    }

    // Fake implementation of ReceiptSettingsApi
    private class FakeReceiptSettingsApi : ReceiptSettingsApi {
        private var getResponse = Response.success(ReceiptSettingsResponse(
            tenantId = "default", outletId = "default",
            headerStoreName = "Default", headerOutletName = "Default",
            headerAddress = null, headerPhone = null, headerAdditionalText = null,
            showSku = false, showModifiers = false, showItemNotes = false,
            showCashier = false, showCustomer = false,
            footerThankYouText = "Default", footerPromoText = null,
            templateVersion = 0, createdAt = null, updatedAt = null
        ))
        private var putResponse = getResponse
        private var getFailure: Throwable? = null
        private var putFailure: Throwable? = null

        fun setGetResponse(response: Response<ReceiptSettingsResponse>) {
            getResponse = response
            getFailure = null
        }

        fun setPutResponse(response: Response<ReceiptSettingsResponse>) {
            putResponse = response
            putFailure = null
        }

        fun setGetFailure(failure: Throwable) {
            getFailure = failure
        }

        override suspend fun getReceiptSettings(outletId: String): Response<ReceiptSettingsResponse> {
            getFailure?.let { throw it }
            return getResponse
        }

        override suspend fun putReceiptSettings(outletId: String, request: UpdateReceiptSettingsRequest): Response<ReceiptSettingsResponse> {
            putFailure?.let { throw it }
            return putResponse
        }
    }

    // Fake implementation of ReceiptSettingsDataStore
    private class FakeReceiptSettingsDataStore : ReceiptSettingsDataStore(InMemoryPreferencesDataStore()) {
        var saveCalled = false
        var lastSettings: ReceiptSettings? = null
        private var loadReturn: ReceiptSettings? = null

        fun setLoadReturn(settings: ReceiptSettings?) {
            loadReturn = settings
        }

        override suspend fun loadSettings(tenantId: String, outletId: String): ReceiptSettings? {
            return loadReturn
        }

        override suspend fun saveSettings(tenantId: String, outletId: String, settings: ReceiptSettings) {
            saveCalled = true
            lastSettings = settings
        }
    }
}