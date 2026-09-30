package com.kasirkita.pos.presentation.product

import com.kasirkita.pos.data.repository.CartRepositoryImpl
import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.model.Stock
import com.kasirkita.pos.domain.model.StockAdjustmentType
import com.kasirkita.pos.domain.repository.OutletRepository
import com.kasirkita.pos.domain.repository.ProductRepository
import com.kasirkita.pos.domain.repository.StockRepository
import com.kasirkita.pos.domain.usecase.AddToCartUseCase
import com.kasirkita.pos.domain.usecase.GetProductsUseCase
import com.kasirkita.pos.domain.usecase.GetStocksUseCase
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductCatalogViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun cachedProductsRemainAvailableWhenStockAndRefreshAreOffline() {
        val releaseRefresh = CompletableDeferred<Unit>()
        val productRepository = FakeProductRepository(
            cachedProducts = listOf(product()),
            refreshResult = Result.failure(IOException("network unavailable")),
            refreshGate = releaseRefresh,
        )
        val stockRepository = FakeStockRepository(
            result = Result.failure(IOException("network unavailable")),
        )
        val cartRepository = CartRepositoryImpl()
        val viewModel = ProductCatalogViewModel(
            getProductsUseCase = GetProductsUseCase(productRepository),
            getStocksUseCase = GetStocksUseCase(stockRepository),
            productRepository = productRepository,
            outletRepository = FakeOutletRepository(),
            addToCartUseCase = AddToCartUseCase(cartRepository),
        )

        dispatcher.scheduler.runCurrent()

        val cacheFirstState = viewModel.state.value as ProductCatalogState.Success
        assertEquals("product-id", cacheFirstState.items.single().product.id)

        releaseRefresh.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value as ProductCatalogState.Success
        val item = state.items.single()
        assertEquals("product-id", item.product.id)
        assertNull(item.stockQuantity)
        assertTrue(item.canAddToCart)

        viewModel.addToCart(item)
        assertEquals("product-id", cartRepository.getCart().value.items.single().productId)
    }

    private class FakeProductRepository(
        private val cachedProducts: List<Product>,
        private val refreshResult: Result<List<Product>>,
        private val refreshGate: CompletableDeferred<Unit>? = null,
    ) : ProductRepository {
        override suspend fun getProducts(): Result<List<Product>> =
            Result.success(cachedProducts)

        override suspend fun refreshProducts(): Result<List<Product>> {
            refreshGate?.await()
            return refreshResult
        }

        override suspend fun createProduct(
            name: String,
            sku: String,
            categoryId: String?,
            price: Long,
            cost: Long,
            minimumStock: Int,
            trackStock: Boolean,
        ): Result<Product> = error("Not used")

        override suspend fun updateProduct(
            productId: String,
            name: String?,
            sku: String?,
            categoryId: String?,
            categoryIdChanged: Boolean,
            price: Long?,
            cost: Long?,
            minimumStock: Int?,
            trackStock: Boolean?,
        ): Result<Product> = error("Not used")
    }

    private class FakeStockRepository(
        private val result: Result<List<Stock>>,
    ) : StockRepository {
        override suspend fun getStocks(outletId: String): Result<List<Stock>> = result

        override suspend fun createAdjustment(
            outletId: String,
            productId: String,
            adjustmentType: StockAdjustmentType,
            quantity: Int,
            reason: String?,
        ): Result<Stock> = error("Not used")
    }

    private class FakeOutletRepository : OutletRepository {
        override val selectedOutlet: StateFlow<Outlet?> = MutableStateFlow(
            Outlet(
                id = "outlet-id",
                tenantId = "tenant-id",
                name = "Outlet Utama",
                address = null,
                isActive = true,
                createdAt = "2026-09-30T00:00:00Z",
            ),
        )

        override suspend fun getOutlets(): Result<List<Outlet>> = error("Not used")

        override suspend fun selectOutlet(outlet: Outlet) = Unit

        override suspend fun clearSelectedOutlet(tenantId: String?, userId: String?) = Unit

        override suspend fun restoreSelectedOutlet() = Unit
    }

    private companion object {
        fun product() = Product(
            id = "product-id",
            tenantId = "tenant-id",
            categoryId = null,
            name = "Kopi Susu",
            sku = "KOPI-SUSU",
            price = 20_000L,
            cost = 10_000L,
            minimumStock = 5,
            trackStock = true,
            isActive = true,
            createdAt = "2026-09-30T00:00:00Z",
        )
    }
}
