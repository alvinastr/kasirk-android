package com.kasirkita.pos.data.repository

import com.kasirkita.pos.core.database.dao.ProductDao
import com.kasirkita.pos.core.database.entity.ProductEntity
import com.kasirkita.pos.data.api.ProductApi
import com.kasirkita.pos.data.local.ProductLocalDataSource
import com.kasirkita.pos.data.model.CreateProductRequest
import com.kasirkita.pos.data.model.ProductResponse
import com.kasirkita.pos.data.model.UpdateProductRequest
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
    private lateinit var repository: ProductRepositoryImpl

    @Before
    fun setUp() {
        api = FakeProductApi()
        dao = FakeProductDao()
        repository = ProductRepositoryImpl(
            productApi = api,
            localDataSource = ProductLocalDataSource(dao),
        )
    }

    @Test
    fun createProduct_mapsApiResponseToDomainProduct() = runBlocking {
        api.createResponse = sampleResponse(trackStock = false)

        val product = repository.createProduct(
            name = "Kopi Susu",
            sku = "KOPISUSU002",
            categoryId = null,
            price = 15_000L,
            cost = 8_000L,
            minimumStock = 0,
            trackStock = false,
        ).getOrThrow()

        assertEquals(PRODUCT_ID, product.id)
        assertEquals("Kopi Susu", product.name)
        assertEquals(15_000L, product.price)
        assertFalse(product.trackStock)
        assertEquals(false, api.lastCreateRequest?.trackStock)
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
    fun getProducts_whenCacheExists_keepsCacheFirstBehavior() = runBlocking {
        dao.products += sampleResponse(trackStock = true).toEntity()

        val products = repository.getProducts().getOrThrow()

        assertEquals(1, products.size)
        assertEquals(PRODUCT_ID, products.single().id)
        assertTrue(products.single().trackStock)
        assertEquals(0, api.getProductsCallCount)
    }

    private fun sampleResponse(trackStock: Boolean) = ProductResponse(
        id = PRODUCT_ID,
        tenantId = "tenant-id",
        categoryId = null,
        name = "Kopi Susu",
        sku = "KOPISUSU002",
        price = 15_000L,
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
            request: UpdateProductRequest,
        ): ProductResponse {
            updateFailure?.let { throwable -> throw throwable }
            return requireNotNull(updateResponse)
        }
    }

    private class FakeProductDao : ProductDao {
        val products = mutableListOf<ProductEntity>()

        override suspend fun getProducts(): List<ProductEntity> = products.toList()

        override suspend fun insertProducts(products: List<ProductEntity>) {
            this.products += products
        }

        override suspend fun deleteAll() {
            products.clear()
        }
    }

    private companion object {
        const val PRODUCT_ID = "product-id"
    }
}
