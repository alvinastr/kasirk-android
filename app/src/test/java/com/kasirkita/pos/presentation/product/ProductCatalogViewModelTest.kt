package com.kasirkita.pos.presentation.product

import com.kasirkita.pos.data.repository.CartRepositoryImpl
import com.kasirkita.pos.domain.model.Cart
import com.kasirkita.pos.domain.model.CartModifierSelectionSnapshot
import com.kasirkita.pos.domain.model.ModifierGroup
import com.kasirkita.pos.domain.model.ModifierOption
import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.model.SelectionMode
import com.kasirkita.pos.domain.model.Stock
import com.kasirkita.pos.domain.model.StockAdjustmentType
import com.kasirkita.pos.domain.repository.CartRepository
import com.kasirkita.pos.domain.repository.CartUpdateResult
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    private fun fakeCategoryRepo() = object : com.kasirkita.pos.domain.repository.CategoryRepository {
        override suspend fun getCategories(): Result<List<com.kasirkita.pos.domain.model.Category>> =
            Result.success(emptyList())
        override suspend fun createCategory(name: String): Result<com.kasirkita.pos.domain.model.Category> = Result.failure(UnsupportedOperationException("Not implemented"))
        override suspend fun updateCategory(id: String, name: String): Result<com.kasirkita.pos.domain.model.Category> = Result.failure(UnsupportedOperationException("Not implemented"))
        override suspend fun deleteCategory(id: String): Result<Unit> = Result.failure(UnsupportedOperationException("Not implemented"))
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
            getCategoriesUseCase = com.kasirkita.pos.domain.usecase.GetCategoriesUseCase(fakeCategoryRepo()),
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

    @Test
    fun directProductStillAddsWithoutOpening() {
        val directRepo = FakeProductRepository(listOf(product()), Result.failure(IOException("offline")))
        val cart = CartRepositoryImpl()
        val vm = ProductCatalogViewModel(
            GetProductsUseCase(directRepo),
            com.kasirkita.pos.domain.usecase.GetCategoriesUseCase(fakeCategoryRepo()),
            GetStocksUseCase(FakeStockRepository(Result.failure(IOException("offline")))),
            directRepo, FakeOutletRepository(), AddToCartUseCase(cart),
        )
        dispatcher.scheduler.advanceUntilIdle()
        val item = (vm.state.value as ProductCatalogState.Success).items.single()
        vm.addToCart(item)
        assertEquals(1, cart.getCart().value.items.size)
        assertEquals("product-id", cart.getCart().value.items.single().productId)
    }

    @Test
    fun configuredProductOpensModifierSelectorAndOnlyValidAddSucceeds() {
        val configured = product().copy(modifierGroups = listOf(ModifierGroup(
            "temp", "tenant-id", "Suhu", true, 0, true, SelectionMode.SINGLE,
            listOf(ModifierOption("ice", "temp", "Ice", 5_000L, true, 0)),
        )))
        val directRepo = FakeProductRepository(listOf(configured), Result.failure(IOException("offline")))
        val cart = CartRepositoryImpl()
        val vm = ProductCatalogViewModel(
            GetProductsUseCase(directRepo),
            com.kasirkita.pos.domain.usecase.GetCategoriesUseCase(fakeCategoryRepo()),
            GetStocksUseCase(FakeStockRepository(Result.failure(IOException("offline")))),
            directRepo, FakeOutletRepository(), AddToCartUseCase(cart),
        )
        dispatcher.scheduler.advanceUntilIdle()
        val item = (vm.state.value as ProductCatalogState.Success).items.single()

        assertNull(vm.modifierSelection.value)
        vm.addToCart(item)
        assertNotNull(vm.modifierSelection.value)
        assertTrue(vm.modifierSelection.value!!.selectedOptionIds.isEmpty())

        vm.confirmModifiers()
        assertTrue("Invalid state should not add to cart", cart.getCart().value.items.isEmpty())
        assertNotNull("Selector should remain open when invalid", vm.modifierSelection.value)

        vm.toggleModifier("ice")
        vm.updateModifierNote("  Sedikit es  ")
        dispatcher.scheduler.advanceUntilIdle()

        vm.confirmModifiers()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull("Selector should close after valid add", vm.modifierSelection.value)
        assertEquals(1, cart.getCart().value.items.size)
        val line = cart.getCart().value.items.single()
        assertEquals("Sedikit es", line.note)
        assertEquals(25_000L, line.price)
    }

    @Test
    fun cancelModifierSelectorDoesNotModifyCartAndReopenStartsFresh() {
        val configured = product().copy(modifierGroups = listOf(ModifierGroup(
            "temp", "tenant-id", "Suhu", true, 0, false, SelectionMode.SINGLE,
            listOf(ModifierOption("ice", "temp", "Ice", 5_000L, true, 0)),
        )))
        val directRepo = FakeProductRepository(listOf(configured), Result.failure(IOException("offline")))
        val cart = CartRepositoryImpl()
        val vm = ProductCatalogViewModel(
            GetProductsUseCase(directRepo),
            com.kasirkita.pos.domain.usecase.GetCategoriesUseCase(fakeCategoryRepo()),
            GetStocksUseCase(FakeStockRepository(Result.failure(IOException("offline")))),
            directRepo, FakeOutletRepository(), AddToCartUseCase(cart),
        )
        dispatcher.scheduler.advanceUntilIdle()
        val item = (vm.state.value as ProductCatalogState.Success).items.single()

        vm.addToCart(item)
        vm.toggleModifier("ice")
        vm.updateModifierNote("test")

        vm.cancelModifiers()
        assertNull(vm.modifierSelection.value)
        assertTrue(cart.getCart().value.items.isEmpty())

        vm.addToCart(item)
        assertTrue(vm.modifierSelection.value!!.selectedOptionIds.isEmpty())
        assertNull(vm.modifierSelection.value!!.note)
    }

    @Test
    fun unknownModifiersDoesNotOpenSelectorAndDoesNotMutateCart() {
        val unknownRepo = FakeProductRepository(
            listOf(product().copy(modifierMetadataLoaded = false)),
            Result.failure(IOException("offline"))
        )
        val cart = CartRepositoryImpl()
        val vm = ProductCatalogViewModel(
            GetProductsUseCase(unknownRepo),
            com.kasirkita.pos.domain.usecase.GetCategoriesUseCase(fakeCategoryRepo()),
            GetStocksUseCase(FakeStockRepository(Result.failure(IOException("offline")))),
            unknownRepo, FakeOutletRepository(), AddToCartUseCase(cart),
        )
        dispatcher.scheduler.advanceUntilIdle()
        val item = (vm.state.value as ProductCatalogState.Success).items.single()

        vm.addToCart(item)
        assertNull(vm.modifierSelection.value)
        assertTrue(cart.getCart().value.items.isEmpty())
    }

    @Test
    fun unavailableProductDoesNotOpenSelectorAndDoesNotMutateCart() {
        val unavailableRepo = FakeProductRepository(
            listOf(product().copy(isActive = false)),
            Result.failure(IOException("offline"))
        )
        val cart = CartRepositoryImpl()
        val vm = ProductCatalogViewModel(
            GetProductsUseCase(unavailableRepo),
            com.kasirkita.pos.domain.usecase.GetCategoriesUseCase(fakeCategoryRepo()),
            GetStocksUseCase(FakeStockRepository(Result.failure(IOException("offline")))),
            unavailableRepo, FakeOutletRepository(), AddToCartUseCase(cart),
        )
        dispatcher.scheduler.advanceUntilIdle()
        val item = (vm.state.value as ProductCatalogState.Success).items.single()

        vm.addToCart(item)
        assertNull(vm.modifierSelection.value)
        assertTrue(cart.getCart().value.items.isEmpty())
    }

    @Test
    fun requiredSingleWithZeroActiveOptionsHasCanAddFalse() {
        val state = ModifierSelectionState.create(
            product().copy(modifierGroups = listOf(
                ModifierGroup("grp", "tenant", "Name", true, 0, true, SelectionMode.SINGLE, emptyList())
            ))
        )
        assertFalse(state.canAdd)
    }

    @Test
    fun requiredMultipleHasCanAddFalse() {
        val state = ModifierSelectionState.create(
            product().copy(modifierGroups = listOf(
                ModifierGroup("grp", "tenant", "Name", true, 0, true, SelectionMode.MULTIPLE,
                    listOf(ModifierOption("opt", "grp", "Option", 0L, true, 0))
                )
            ))
        )
        assertFalse(state.canAdd)
    }

    @Test
    fun inactiveRequiredGroupDoesNotBlockAdd() {
        val state = ModifierSelectionState.create(
            product().copy(modifierGroups = listOf(
                ModifierGroup("grp", "tenant", "Name", false, 0, true, SelectionMode.SINGLE,
                    listOf(ModifierOption("opt", "grp", "Option", 0L, true, 0))
                )
            ))
        )
        assertTrue(state.canAdd)
    }

    @Test
    fun inactiveOptionCannotBecomeSelectedSnapshot() {
        val state = ModifierSelectionState.create(
            product().copy(modifierGroups = listOf(
                ModifierGroup("grp", "tenant", "Name", true, 0, false, SelectionMode.MULTIPLE,
                    listOf(ModifierOption("opt", "grp", "Option", 100L, false, 0))
                )
            ))
        )
        val snapshots = state.selectedSnapshots()
        assertTrue(snapshots.isEmpty())
    }

    @Test
    fun invalidStateNeverCallsConfiguredCartAdd() {
        val configured = product().copy(modifierGroups = listOf(ModifierGroup(
            "temp", "tenant-id", "Suhu", true, 0, true, SelectionMode.SINGLE,
            listOf(ModifierOption("ice", "temp", "Ice", 5_000L, true, 0)),
        )))
        var addCallCount = 0
        val fakeCart = object : CartRepository {
            override fun getCart() = MutableStateFlow(Cart(emptyList()))
            override fun addProduct(product: Product, availableStock: Int?): CartUpdateResult {
                addCallCount++
                return CartUpdateResult.UPDATED
            }
            override fun addConfiguredProduct(
                product: Product,
                selectedModifiers: List<CartModifierSelectionSnapshot>,
                note: String?,
                availableStock: Int?,
            ): CartUpdateResult {
                addCallCount++
                return CartUpdateResult.UPDATED
            }
            override fun removeProduct(lineKey: String) {}
            override fun updateQuantity(lineKey: String, quantity: Int): CartUpdateResult = CartUpdateResult.UPDATED
            override fun clearCart() {}
        }
        val vm = ProductCatalogViewModel(
            GetProductsUseCase(FakeProductRepository(listOf(configured), Result.failure(IOException("offline")))),
            com.kasirkita.pos.domain.usecase.GetCategoriesUseCase(fakeCategoryRepo()),
            GetStocksUseCase(FakeStockRepository(Result.failure(IOException("offline")))),
            FakeProductRepository(listOf(configured), Result.failure(IOException("offline"))),
            FakeOutletRepository(),
            AddToCartUseCase(fakeCart),
        )
        dispatcher.scheduler.advanceUntilIdle()
        val item = (vm.state.value as ProductCatalogState.Success).items.single()

        vm.addToCart(item)
        vm.confirmModifiers()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Invalid state should not call add", 0, addCallCount)
    }

    @Test
    fun validConfiguredAddCallsCartApiExactlyOnceWithCorrectParams() {
        val configured = product().copy(modifierGroups = listOf(ModifierGroup(
            "temp", "tenant-id", "Suhu", true, 0, false, SelectionMode.SINGLE,
            listOf(ModifierOption("ice", "temp", "Ice", 5_000L, true, 0)),
        )))
        val capturedCalls = mutableListOf<Triple<Product, List<CartModifierSelectionSnapshot>, String?>>()
        val fakeCart = object : CartRepository {
            override fun getCart() = MutableStateFlow(Cart(emptyList()))
            override fun addProduct(product: Product, availableStock: Int?): CartUpdateResult =
                CartUpdateResult.UPDATED
            override fun addConfiguredProduct(
                product: Product,
                selectedModifiers: List<CartModifierSelectionSnapshot>,
                note: String?,
                availableStock: Int?,
            ): CartUpdateResult {
                capturedCalls.add(Triple(product, selectedModifiers, note))
                return CartUpdateResult.UPDATED
            }
            override fun removeProduct(lineKey: String) {}
            override fun updateQuantity(lineKey: String, quantity: Int): CartUpdateResult = CartUpdateResult.UPDATED
            override fun clearCart() {}
        }
        val vm = ProductCatalogViewModel(
            GetProductsUseCase(FakeProductRepository(listOf(configured), Result.failure(IOException("offline")))),
            com.kasirkita.pos.domain.usecase.GetCategoriesUseCase(fakeCategoryRepo()),
            GetStocksUseCase(FakeStockRepository(Result.failure(IOException("offline")))),
            FakeProductRepository(listOf(configured), Result.failure(IOException("offline"))),
            FakeOutletRepository(),
            AddToCartUseCase(fakeCart),
        )
        dispatcher.scheduler.advanceUntilIdle()
        val item = (vm.state.value as ProductCatalogState.Success).items.single()

        vm.addToCart(item)
        vm.toggleModifier("ice")
        vm.updateModifierNote("  Test note  ")
        vm.confirmModifiers()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Should call add exactly once", 1, capturedCalls.size)
        val (product, snapshots, note) = capturedCalls.single()
        assertEquals(configured.id, product.id)
        assertEquals(1, snapshots.size)
        val snapshot = snapshots.single()
        assertEquals("ice", snapshot.optionId)
        assertEquals("temp", snapshot.groupId)
        assertEquals("Suhu", snapshot.groupName)
        assertEquals("Ice", snapshot.optionName)
        assertEquals(5_000L, snapshot.priceDelta)
        assertEquals("Test note", note)
    }

    @Test
    fun configuredAddFailureKeepsSelectorOpenAndCartUnmutated() {
        val configured = product().copy(modifierGroups = listOf(ModifierGroup(
            "temp", "tenant-id", "Suhu", true, 0, false, SelectionMode.SINGLE,
            listOf(ModifierOption("ice", "temp", "Ice", 5_000L, true, 0)),
        )))
        val failingCart = object : CartRepository {
            override fun getCart() = MutableStateFlow(Cart(emptyList()))
            override fun addProduct(product: Product, availableStock: Int?): CartUpdateResult =
                CartUpdateResult.UPDATED
            override fun addConfiguredProduct(
                product: Product,
                selectedModifiers: List<CartModifierSelectionSnapshot>,
                note: String?,
                availableStock: Int?,
            ): CartUpdateResult = CartUpdateResult.STOCK_LIMIT_REACHED
            override fun removeProduct(lineKey: String) {}
            override fun updateQuantity(lineKey: String, quantity: Int): CartUpdateResult = CartUpdateResult.UPDATED
            override fun clearCart() {}
        }
        val vm = ProductCatalogViewModel(
            GetProductsUseCase(FakeProductRepository(listOf(configured), Result.failure(IOException("offline")))),
            com.kasirkita.pos.domain.usecase.GetCategoriesUseCase(fakeCategoryRepo()),
            GetStocksUseCase(FakeStockRepository(Result.failure(IOException("offline")))),
            FakeProductRepository(listOf(configured), Result.failure(IOException("offline"))),
            FakeOutletRepository(),
            AddToCartUseCase(failingCart),
        )
        dispatcher.scheduler.advanceUntilIdle()
        val item = (vm.state.value as ProductCatalogState.Success).items.single()

        vm.addToCart(item)
        vm.toggleModifier("ice")
        vm.confirmModifiers()
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNull("Selector should remain open on failure", vm.modifierSelection.value)
        assertTrue("Cart should not be mutated", failingCart.getCart().value.items.isEmpty())
    }

    @Test
    fun negativePriceDeltaOptionBlocksValidAdd() {
        val state = ModifierSelectionState.create(
            product().copy(modifierGroups = listOf(ModifierGroup(
                "disc", "tenant", "Discount", true, 0, false, SelectionMode.SINGLE,
                listOf(ModifierOption("neg", "disc", "Negative", -1_000L, true, 0))
            ))
        ))
        val updated = state.toggleOption("neg")
        assertFalse("Negative priceDelta should block add", updated.canAdd)
    }

    @Test
    fun noteCannotExceed255InState() {
        val state = ModifierSelectionState.create(
            product().copy(modifierGroups = listOf(ModifierGroup(
                "grp", "tenant", "Name", true, 0, false, SelectionMode.SINGLE,
                listOf(ModifierOption("opt", "grp", "Option", 0L, true, 0))
            ))
        ))
        val note255 = "a".repeat(255)
        val note256 = "a".repeat(256)

        val accepted = state.updateNote(note255)
        assertEquals(note255, accepted.note)
        assertNull(accepted.noteFeedback)

        val rejected = state.updateNote(note256)
        assertNull(rejected.note)
        assertEquals("Catatan maksimal 255 karakter", rejected.noteFeedback)
    }

    @Test
    fun dialogDoesNotCallProductRepositoryOnOpen() {
        var refreshCalled = false
        val spyRepo = FakeProductRepository(
            listOf(product().copy(modifierGroups = listOf(ModifierGroup(
                "grp", "tenant", "Name", true, 0, false, SelectionMode.SINGLE,
                listOf(ModifierOption("opt", "grp", "Option", 0L, true, 0))
            )))),
            Result.failure(IOException("should not refresh"))
        ) {
            refreshCalled = true
        }
        val vm = ProductCatalogViewModel(
            GetProductsUseCase(spyRepo),
            com.kasirkita.pos.domain.usecase.GetCategoriesUseCase(fakeCategoryRepo()),
            GetStocksUseCase(FakeStockRepository(Result.failure(IOException("offline")))),
            spyRepo, FakeOutletRepository(), AddToCartUseCase(CartRepositoryImpl()),
        )
        dispatcher.scheduler.advanceUntilIdle()
        refreshCalled = false
        val item = (vm.state.value as ProductCatalogState.Success).items.single()

        vm.addToCart(item)
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(refreshCalled)
        assertTrue("Opening selector should not call refreshProducts", refreshCalled == false)
        assertNotNull(vm.modifierSelection.value)
        assertTrue("Selector opened with cached data", vm.modifierSelection.value != null)
    }

    private class FakeProductRepository(
        private val cachedProducts: List<Product>,
        private val refreshResult: Result<List<Product>>,
        private val refreshGate: CompletableDeferred<Unit>? = null,
        private val onRefresh: (() -> Unit)? = null,
    ) : ProductRepository {
        override suspend fun getProducts(
            query: String?,
            categoryId: String?,
            includeModifiers: Boolean?,
        ): Result<List<Product>> =
            Result.success(cachedProducts)

        override suspend fun refreshProducts(
            query: String?,
            categoryId: String?,
            includeModifiers: Boolean?,
        ): Result<List<Product>> {
            onRefresh?.invoke()
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
            modifierMetadataLoaded = true,
        )
    }
}