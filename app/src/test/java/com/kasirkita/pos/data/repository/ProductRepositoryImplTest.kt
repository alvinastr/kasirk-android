package com.kasirkita.pos.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.google.gson.JsonObject
import com.kasirkita.pos.core.database.dao.ProductDao
import com.kasirkita.pos.core.database.entity.ProductEntity
import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.data.api.ProductApi
import com.kasirkita.pos.data.local.ProductLocalDataSource
import com.kasirkita.pos.data.model.CreateProductRequest
import com.kasirkita.pos.data.model.ProductResponse
import com.kasirkita.pos.domain.model.AuthSession
import com.kasirkita.pos.domain.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class ProductRepositoryImplTest {

    private lateinit var api: FakeProductApi
    private lateinit var dao: FakeProductDao
    private lateinit var sessionStore: AuthSessionDataStore
    private lateinit var repository: ProductRepositoryImpl

    @Before
    fun setUp() {
        api = FakeProductApi()
        dao = FakeProductDao()
        sessionStore = AuthSessionDataStore(InMemoryPreferencesDataStore())
        runBlocking { sessionStore.saveSession(session()) }
        repository = ProductRepositoryImpl(
            productApi = api,
            localDataSource = ProductLocalDataSource(dao),
            authSessionDataStore = sessionStore,
        )
    }

    @Test
    fun createProduct_mapsApiResponseToDomainProduct() = runBlocking {
        api.createResponse = sampleResponse(trackStock = false)

        val product = repository.createProduct(
            name = "Kopi Susu",
            sku = "KOPISUSU002",
            categoryId = CATEGORY_ID,
            price = 15_000L,
            cost = 8_000L,
            minimumStock = 0,
            trackStock = false,
        ).getOrThrow()

        assertEquals(PRODUCT_ID, product.id)
        assertEquals("Kopi Susu", product.name)
        assertEquals(15_000L, product.price)
        assertFalse(product.trackStock)
        assertEquals(
            CreateProductRequest(
                name = "Kopi Susu",
                sku = "KOPISUSU002",
                categoryId = CATEGORY_ID,
                price = 15_000L,
                cost = 8_000L,
                minimumStock = 0,
                trackStock = false,
            ),
            api.lastCreateRequest,
        )
    }

    @Test
    fun updateProduct_whenApiFails_returnsFailure() = runBlocking {
        val expected = IOException("network unavailable")
        api.updateFailure = expected

        val result = repository.updateProduct(
            productId = PRODUCT_ID,
            name = "Nama Baru",
        )

        assertTrue(result.isFailure)
        assertSame(expected, result.exceptionOrNull())
    }

    @Test
    fun updateProduct_sendsOnlyChangedFieldsAndMapsResponse() = runBlocking {
        api.updateResponse = sampleResponse(
            trackStock = false,
            price = 17_000L,
        )

        val product = repository.updateProduct(
            productId = PRODUCT_ID,
            price = 17_000L,
            trackStock = false,
        ).getOrThrow()

        assertEquals(PRODUCT_ID, api.lastUpdateProductId)
        assertEquals(17_000L, api.lastUpdateRequest?.get("price")?.asLong)
        assertFalse(api.lastUpdateRequest?.get("track_stock")?.asBoolean ?: true)
        assertEquals(2, api.lastUpdateRequest?.size())
        assertEquals(17_000L, product.price)
        assertFalse(product.trackStock)
    }

    @Test
    fun getProducts_whenCacheExists_keepsCacheFirstBehavior() = runBlocking {
        dao.products += sampleResponse(trackStock = true).toEntity()

        val products = repository.getProducts().getOrThrow()

        assertEquals(1, products.size)
        assertEquals(PRODUCT_ID, products.single().id)
        assertTrue(products.single().trackStock)
        assertEquals(0, api.getProductsCallCount)
    }

    @Test
    fun getProducts_doesNotExposeAnotherTenantsCache() = runBlocking {
        dao.products += sampleResponse(
            trackStock = true,
            tenantId = OTHER_TENANT_ID,
        ).toEntity()
        api.productsResponse = listOf(sampleResponse(trackStock = false))

        val products = repository.getProducts().getOrThrow()

        assertEquals(listOf(TENANT_ID), products.map { product -> product.tenantId })
        assertEquals(1, api.getProductsCallCount)
        assertEquals(1, dao.getProducts(OTHER_TENANT_ID).size)
    }

    @Test
    fun refreshProducts_replacesOnlyTheCurrentTenantsCache() = runBlocking {
        dao.products += sampleResponse(
            trackStock = true,
            tenantId = OTHER_TENANT_ID,
        ).toEntity()
        api.productsResponse = listOf(sampleResponse(trackStock = false))

        repository.refreshProducts().getOrThrow()

        assertEquals(1, dao.getProducts(TENANT_ID).size)
        assertEquals(1, dao.getProducts(OTHER_TENANT_ID).size)
    }

    @Test
    fun refreshProducts_rejectsAProductOwnedByAnotherTenant() = runBlocking {
        api.productsResponse = listOf(
            sampleResponse(
                trackStock = true,
                tenantId = OTHER_TENANT_ID,
            ),
        )

        val result = repository.refreshProducts()

        assertTrue(result.isFailure)
        assertTrue(dao.getProducts(TENANT_ID).isEmpty())
    }

    @Test
    fun getProducts_withoutSession_failsWithoutReadingCacheOrApi() = runBlocking {
        sessionStore.clearSession()

        val result = repository.getProducts()

        assertTrue(result.isFailure)
        assertEquals(0, dao.readCount)
        assertEquals(0, api.getProductsCallCount)
    }

    private fun sampleResponse(
        trackStock: Boolean,
        price: Long = 15_000L,
        tenantId: String = TENANT_ID,
    ) = ProductResponse(
        id = PRODUCT_ID,
        tenantId = tenantId,
        categoryId = null,
        name = "Kopi Susu",
        sku = "KOPISUSU002",
        price = price,
        cost = 8_000L,
        minimumStock = 0,
        trackStock = trackStock,
        isActive = true,
        createdAt = "2026-09-21T00:00:00.000Z",
    )

    private class FakeProductApi : ProductApi {
        var getProductsCallCount = 0
        var productsResponse: List<ProductResponse> = emptyList()
        var createResponse: ProductResponse? = null
        var updateResponse: ProductResponse? = null
        var updateFailure: Throwable? = null
        var lastCreateRequest: CreateProductRequest? = null
        var lastUpdateProductId: String? = null
        var lastUpdateRequest: JsonObject? = null

        override suspend fun getProducts(): List<ProductResponse> {
            getProductsCallCount++
            return productsResponse
        }

        override suspend fun createProduct(
            request: CreateProductRequest,
        ): ProductResponse {
            lastCreateRequest = request
            return requireNotNull(createResponse)
        }

        override suspend fun updateProduct(
            productId: String,
            request: JsonObject,
        ): ProductResponse {
            lastUpdateProductId = productId
            lastUpdateRequest = request
            updateFailure?.let { throwable -> throw throwable }
            return requireNotNull(updateResponse)
        }
    }

    private class FakeProductDao : ProductDao {
        val products = mutableListOf<ProductEntity>()
        var readCount = 0

        override suspend fun getProducts(tenantId: String): List<ProductEntity> {
            readCount++
            return products.filter { product -> product.tenantId == tenantId }
        }

        override suspend fun insertProducts(products: List<ProductEntity>) {
            products.forEach { product ->
                this.products.removeAll { stored ->
                    stored.tenantId == product.tenantId && stored.id == product.id
                }
                this.products += product
            }
        }

        override suspend fun deleteAll(tenantId: String) {
            products.removeAll { product -> product.tenantId == tenantId }
        }
    }

    private fun session() = AuthSession(
        userId = USER_ID,
        userName = "Kasir Utama",
        tenantId = TENANT_ID,
        role = UserRole.CASHIER,
        outletId = "outlet-id",
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresAt = Long.MAX_VALUE,
        deviceId = "device-id",
    )

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences = transform(state.value).also { updated ->
            state.value = updated
        }
    }

    private companion object {
        const val PRODUCT_ID = "product-id"
        const val CATEGORY_ID = "category-id"
        const val TENANT_ID = "tenant-id"
        const val OTHER_TENANT_ID = "other-tenant-id"
        const val USER_ID = "user-id"
    }
}
