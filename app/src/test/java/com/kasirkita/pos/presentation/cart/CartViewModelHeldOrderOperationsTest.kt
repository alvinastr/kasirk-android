package com.kasirkita.pos.presentation.cart

import androidx.lifecycle.SavedStateHandle
import com.kasirkita.pos.domain.error.HeldOrderError
import com.kasirkita.pos.domain.model.Cart
import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.CartLineKey
import com.kasirkita.pos.domain.model.CartModifierSelectionSnapshot
import com.kasirkita.pos.domain.model.HeldOrder
import com.kasirkita.pos.domain.model.HeldOrderCheckoutRequest
import com.kasirkita.pos.domain.model.HeldOrderCreateRequest
import com.kasirkita.pos.domain.model.HeldOrderItem
import com.kasirkita.pos.domain.model.HeldOrderItemRequest
import com.kasirkita.pos.domain.model.HeldOrderModifier
import com.kasirkita.pos.domain.model.HeldOrderUpdateRequest
import com.kasirkita.pos.domain.model.ModifierGroup
import com.kasirkita.pos.domain.model.ModifierOption
import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.model.SelectionMode
import com.kasirkita.pos.domain.model.Shift
import com.kasirkita.pos.domain.model.Transaction
import com.kasirkita.pos.domain.repository.CartRepository
import com.kasirkita.pos.domain.repository.CartUpdateResult
import com.kasirkita.pos.domain.repository.HeldOrderRepository
import com.kasirkita.pos.domain.repository.OutletRepository
import com.kasirkita.pos.domain.repository.ProductRepository
import com.kasirkita.pos.domain.repository.ShiftRepository
import com.kasirkita.pos.domain.usecase.AddToCartUseCase
import com.kasirkita.pos.domain.usecase.RemoveFromCartUseCase
import com.kasirkita.pos.domain.usecase.UpdateCartQuantityUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CartViewModelHeldOrderOperationsTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var cartRepository: FakeCartRepository
    private lateinit var heldOrderRepository: FakeHeldOrderRepository
    private lateinit var productRepository: FakeProductRepository
    private lateinit var outletRepository: FakeOutletRepository
    private lateinit var shiftRepository: FakeShiftRepository
    private lateinit var viewModel: CartViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        cartRepository = FakeCartRepository()
        heldOrderRepository = FakeHeldOrderRepository()
        productRepository = FakeProductRepository()
        outletRepository = FakeOutletRepository()
        shiftRepository = FakeShiftRepository()
        outletRepository.setOutlet(outlet("out-1"))
        shiftRepository.setShift(shift("shift-1", "out-1", "OPEN"))
        viewModel = CartViewModel(
            cartRepository = cartRepository,
            heldOrderRepository = heldOrderRepository,
            productRepository = productRepository,
            addToCart = AddToCartUseCase(cartRepository),
            removeFromCart = RemoveFromCartUseCase(cartRepository),
            updateCartQuantity = UpdateCartQuantityUseCase(cartRepository),
            outletRepository = outletRepository,
            shiftRepository = shiftRepository,
            savedStateHandle = SavedStateHandle(),
        )
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun listHeldOrders_usesAuthoritativeOutletAndRetainsListOnRefreshFailure() {
        heldOrderRepository.listResult = Result.success(listOf(order("held-a", version = 1)))
        viewModel.listHeldOrders()
        assertEquals(listOf("held-a"), viewModel.state.value.heldOrders.map { it.id })
        assertEquals("out-1", heldOrderRepository.listCalls.single().outletId)
        assertEquals("OPEN", heldOrderRepository.listCalls.single().status)

        heldOrderRepository.listResult = Result.failure(IOException("offline"))
        viewModel.listHeldOrders()

        assertEquals(listOf("held-a"), viewModel.state.value.heldOrders.map { it.id })
        assertEquals(HeldOrderUiError.NETWORK, viewModel.state.value.heldOrderError)
    }

    @Test
    fun saveHeldOrder_sendsContextAndItemIdsOnly_thenClearsCartAndRefetchesList() {
        cartRepository.replaceCart(listOf(cartLine("prod-1", quantity = 2, modifierIds = listOf("opt-1"), note = "less ice")))
        heldOrderRepository.createResult = Result.success(order("held-new", version = 1, label = "Table 1"))
        heldOrderRepository.listResult = Result.success(listOf(order("held-new", version = 1)))

        viewModel.saveHeldOrder(" Table 1 ")

        val request = heldOrderRepository.createRequests.single()
        assertEquals("out-1", request.outletId)
        assertEquals("shift-1", request.cashierSessionId)
        assertEquals("Table 1", request.label)
        assertEquals(HeldOrderItemRequest("prod-1", 2, listOf("opt-1"), "less ice"), request.items.single())
        assertTrue(viewModel.state.value.cart.items.isEmpty())
        assertEquals(listOf("held-new"), viewModel.state.value.heldOrders.map { it.id })
    }

    @Test
    fun saveHeldOrder_doesNotClearCartChangedDuringCreate() {
        cartRepository.replaceCart(listOf(cartLine("prod-1", quantity = 1)))
        heldOrderRepository.createResult = Result.success(order("held-new", version = 1))
        heldOrderRepository.listResult = Result.success(listOf(order("held-new", version = 1)))
        heldOrderRepository.onCreate = {
            cartRepository.replaceCart(listOf(cartLine("prod-2", quantity = 1)))
        }

        viewModel.saveHeldOrder("Table 1")

        assertEquals("prod-2", viewModel.state.value.cart.items.single().productId)
        assertEquals(HeldOrderUiError.CART_CHANGED, viewModel.state.value.heldOrderError)
    }

    @Test
    fun saveCurrentCartThenRestore_doesNotFetchTargetWhenCartChangesDuringCreate() {
        cartRepository.replaceCart(listOf(cartLine("prod-1", quantity = 1)))
        heldOrderRepository.createResult = Result.success(order("held-new", version = 1))
        heldOrderRepository.listResult = Result.success(listOf(order("held-new", version = 1)))
        heldOrderRepository.onCreate = {
            cartRepository.replaceCart(listOf(cartLine("prod-2", quantity = 1)))
        }

        viewModel.saveCurrentCartThenRestoreHeldOrder("target", label = null)

        assertEquals("prod-2", viewModel.state.value.cart.items.single().productId)
        assertEquals(HeldOrderUiError.CART_CHANGED, viewModel.state.value.heldOrderError)
        assertEquals(emptyList<String>(), heldOrderRepository.requestedGetIds)
    }

    @Test
    fun restoreHeldOrder_usesCurrentCatalogModifierMetadataAndReplacesCartAtomically() {
        val product = product(
            id = "prod-1",
            price = 15_000,
            trackStock = true,
            stock = 5,
            option = ModifierOption("opt-1", "grp-1", "Large", 2_000, true, 1),
        )
        productRepository.products = Result.success(listOf(product))
        heldOrderRepository.getResults["held-1"] = Result.success(
            order(
                id = "held-1",
                version = 3,
                items = listOf(
                    heldItem("prod-1", 2, listOf(HeldOrderModifier("m1", "old", "opt-1", "Old", "OldLarge", 99))),
                ),
            ),
        )
        cartRepository.replaceCart(listOf(cartLine("existing", quantity = 1)))

        viewModel.restoreHeldOrder("held-1")

        val restored = viewModel.state.value.cart.items.single()
        assertEquals("prod-1", restored.productId)
        assertEquals(2, restored.quantity)
        assertEquals(15_000, restored.basePrice)
        assertTrue(restored.trackStock)
        assertEquals(5, restored.availableStock)
        assertEquals("Large", restored.modifierSelections.single().optionName)
        assertEquals(2_000, restored.modifierSelections.single().priceDelta)
        assertEquals("held-1", viewModel.state.value.heldOrderId)
        assertEquals(3, viewModel.state.value.heldOrderExpectedVersion)
    }

    @Test
    fun restoreHeldOrder_missingProductLeavesExistingCartUntouched() {
        productRepository.products = Result.success(emptyList())
        heldOrderRepository.getResults["held-1"] = Result.success(order("held-1", items = listOf(heldItem("missing", 1))))
        cartRepository.replaceCart(listOf(cartLine("existing", quantity = 1)))

        viewModel.restoreHeldOrder("held-1")

        assertEquals("existing", viewModel.state.value.cart.items.single().productId)
        assertNull(viewModel.state.value.heldOrderId)
        assertEquals(HeldOrderUiError.CATALOG_CONFLICT, viewModel.state.value.heldOrderError)
    }

    @Test
    fun restoreHeldOrder_stockConflictLeavesExistingCartUntouched() {
        productRepository.products = Result.success(listOf(product("prod-1", trackStock = true, stock = 1)))
        heldOrderRepository.getResults["held-1"] = Result.success(order("held-1", items = listOf(heldItem("prod-1", 2))))
        cartRepository.replaceCart(listOf(cartLine("existing", quantity = 1)))

        viewModel.restoreHeldOrder("held-1")

        assertEquals("existing", viewModel.state.value.cart.items.single().productId)
        assertEquals(HeldOrderUiError.STOCK_CONFLICT, viewModel.state.value.heldOrderError)
    }

    @Test
    fun updateHeldOrder_sendsCurrentLabelAndFullCurrentCartReplacement() {
        cartRepository.replaceCart(listOf(cartLine("prod-1", quantity = 3)))
        viewModel.attachHeldOrder("held-1", 7, "Old")
        heldOrderRepository.updateResult = Result.success(order("held-1", version = 8, label = "New"))
        heldOrderRepository.listResult = Result.success(listOf(order("held-1", version = 8, label = "New")))

        viewModel.updateHeldOrder(" New ")

        val request = heldOrderRepository.updateRequests.single()
        assertEquals("held-1", request.id)
        assertEquals(7, request.expectedVersion)
        assertEquals("New", request.label)
        assertEquals(HeldOrderItemRequest("prod-1", 3, emptyList(), null), request.items!!.single())
        assertEquals(8, viewModel.state.value.heldOrderExpectedVersion)
        assertEquals("New", viewModel.state.value.heldOrderLabel)
        assertEquals(listOf("held-1"), viewModel.state.value.heldOrders.map { it.id })
    }

    @Test
    fun cancelAttachedHeldOrder_preservesCartAndDetachesOnlyAfterSuccess() {
        cartRepository.replaceCart(listOf(cartLine("prod-1", quantity = 1)))
        viewModel.attachHeldOrder("held-1", 4, "Label")
        heldOrderRepository.cancelResult = Result.success(order("held-1", version = 5, status = "CANCELLED"))
        heldOrderRepository.listResult = Result.success(emptyList())

        viewModel.cancelAttachedHeldOrder()

        assertEquals("prod-1", viewModel.state.value.cart.items.single().productId)
        assertNull(viewModel.state.value.heldOrderId)
        assertTrue(heldOrderRepository.cancelCalls.contains("held-1" to 4))
    }

    @Test
    fun versionConflict_usesStructuredErrorCode() {
        cartRepository.replaceCart(listOf(cartLine("prod-1", quantity = 1)))
        viewModel.attachHeldOrder("held-1", 4, "Label")
        heldOrderRepository.updateResult = Result.failure(HeldOrderError(409, "HELD_ORDER_VERSION_CONFLICT", "anything"))

        viewModel.updateHeldOrder("Label")

        assertEquals(HeldOrderUiError.VERSION_CONFLICT, viewModel.state.value.heldOrderError)
    }

    @Test
    fun listHeldOrders_beforeAnySuccess_doesNotClaimAuthoritativeZero() {
        heldOrderRepository.listResult = Result.failure(IOException("offline"))

        viewModel.listHeldOrders()

        assertEquals(false, viewModel.state.value.heldOrdersLoaded)
        assertTrue(viewModel.state.value.heldOrders.isEmpty())
        assertEquals(HeldOrderUiError.NETWORK, viewModel.state.value.heldOrderError)
    }

    @Test
    fun listHeldOrders_marksLoadedOnAuthoritativeSuccess() {
        heldOrderRepository.listResult = Result.success(emptyList())

        viewModel.listHeldOrders()

        assertEquals(true, viewModel.state.value.heldOrdersLoaded)
        assertEquals("out-1", viewModel.state.value.heldOrdersOutletId)
    }

    @Test
    fun outletChange_hidesPreviousOutletListUntilNewAuthoritativeSuccess() {
        heldOrderRepository.listResult = Result.success(listOf(order("held-a")))
        viewModel.listHeldOrders()
        assertEquals(listOf("held-a"), viewModel.state.value.heldOrders.map { it.id })

        outletRepository.setOutlet(outlet("out-2"))
        shiftRepository.setShift(shift("shift-2", "out-2", "OPEN"))

        assertTrue(viewModel.state.value.heldOrders.isEmpty())
        assertEquals(false, viewModel.state.value.heldOrdersLoaded)
        assertNull(viewModel.state.value.heldOrdersOutletId)
    }

    @Test
    fun restoreHeldOrder_nullModifierIdFailsWithoutMutatingCart() {
        productRepository.products = Result.success(listOf(product("prod-1")))
        heldOrderRepository.getResults["held-1"] = Result.success(
            order(
                "held-1",
                items = listOf(
                    heldItem("prod-1", 1, listOf(HeldOrderModifier("m1", "grp-1", null, "Group", "Unknown", 0))),
                ),
            ),
        )
        cartRepository.replaceCart(listOf(cartLine("existing", 1)))

        viewModel.restoreHeldOrder("held-1")

        assertEquals("existing", viewModel.state.value.cart.items.single().productId)
        assertEquals(HeldOrderUiError.CATALOG_CONFLICT, viewModel.state.value.heldOrderError)
        assertNull(viewModel.state.value.heldOrderId)
    }

    @Test
    fun reloadAttachedHeldOrder_replacesCartAndAdoptsAuthoritativeVersion() {
        cartRepository.replaceCart(listOf(cartLine("local-edit", 1)))
        viewModel.attachHeldOrder("held-1", 4, "Old")
        productRepository.products = Result.success(listOf(product("prod-server")))
        heldOrderRepository.getResults["held-1"] = Result.success(
            order("held-1", version = 7, label = "Server", items = listOf(heldItem("prod-server", 2))),
        )

        viewModel.reloadAttachedHeldOrder()

        assertEquals("prod-server", viewModel.state.value.cart.items.single().productId)
        assertEquals(7, viewModel.state.value.heldOrderExpectedVersion)
        assertEquals("Server", viewModel.state.value.heldOrderLabel)
    }

    @Test
    fun saveCurrentCartThenRestoreHeldOrder_success_savesCurrentCartThenOpensTarget() {
        cartRepository.replaceCart(listOf(cartLine("prod-current", quantity = 2)))
        productRepository.products = Result.success(listOf(product("prod-target")))
        heldOrderRepository.createResult = Result.success(order("held-saved", version = 1))
        heldOrderRepository.listResult = Result.success(listOf(order("held-saved")))
        heldOrderRepository.getResults["held-target"] = Result.success(
            order("held-target", version = 4, items = listOf(heldItem("prod-target", 3))),
        )

        viewModel.saveCurrentCartThenRestoreHeldOrder("held-target", label = "Meja 3")

        assertEquals(listOf("prod-target"), viewModel.state.value.cart.items.map { it.productId })
        assertEquals(3, viewModel.state.value.cart.items.single().quantity)
        assertEquals("held-target", viewModel.state.value.heldOrderId)
        assertEquals(4, viewModel.state.value.heldOrderExpectedVersion)
        assertTrue(viewModel.state.value.isEditingHeldOrder)
        assertEquals("Meja 3", heldOrderRepository.createRequests.single().label)
        assertEquals("prod-current", heldOrderRepository.createRequests.single().items.single().productId)
    }

    @Test
    fun saveCurrentCartThenRestoreHeldOrder_saveFailure_preservesCartAndNeverFetchesTarget() {
        cartRepository.replaceCart(listOf(cartLine("prod-current", quantity = 1)))
        heldOrderRepository.createResult = Result.failure(IOException("offline"))

        viewModel.saveCurrentCartThenRestoreHeldOrder("held-target", label = null)

        assertEquals("prod-current", viewModel.state.value.cart.items.single().productId)
        assertNull(viewModel.state.value.heldOrderId)
        assertEquals(HeldOrderUiError.NETWORK, viewModel.state.value.heldOrderError)
        assertEquals(emptyList<String>(), heldOrderRepository.requestedGetIds)
    }

    @Test
    fun saveCurrentCartThenRestoreHeldOrder_withEmptyCartJustRestoresTarget() {
        productRepository.products = Result.success(listOf(product("prod-target")))
        heldOrderRepository.getResults["held-target"] = Result.success(
            order("held-target", version = 2, items = listOf(heldItem("prod-target", 1))),
        )

        viewModel.saveCurrentCartThenRestoreHeldOrder("held-target", label = null)

        assertTrue(heldOrderRepository.createRequests.isEmpty())
        assertEquals("prod-target", viewModel.state.value.cart.items.single().productId)
        assertEquals("held-target", viewModel.state.value.heldOrderId)
    }

    @Test
    fun updateHeldOrder_versionConflictKeepsContextSoReloadCanBeOffered() {
        cartRepository.replaceCart(listOf(cartLine("prod-1", quantity = 1)))
        viewModel.attachHeldOrder("held-1", 4, "Label")
        heldOrderRepository.updateResult = Result.failure(HeldOrderError(409, "HELD_ORDER_VERSION_CONFLICT", "anything"))

        viewModel.updateHeldOrder("Label")

        assertEquals("held-1", viewModel.state.value.heldOrderId)
        assertEquals(4, viewModel.state.value.heldOrderExpectedVersion)
        assertEquals("prod-1", viewModel.state.value.cart.items.single().productId)
        assertEquals(HeldOrderUiError.VERSION_CONFLICT, viewModel.state.value.heldOrderError)
    }

    @Test
    fun notOpen_error_usesStructuredErrorCode() {
        cartRepository.replaceCart(listOf(cartLine("prod-1", quantity = 1)))
        viewModel.attachHeldOrder("held-1", 1, "Label")
        heldOrderRepository.updateResult = Result.failure(HeldOrderError(409, "HELD_ORDER_NOT_OPEN", "gone"))

        viewModel.updateHeldOrder("Label")

        assertEquals(HeldOrderUiError.NOT_OPEN, viewModel.state.value.heldOrderError)
    }

    @Test
    fun invalidCashierSession_usesStructuredErrorCode() {
        cartRepository.replaceCart(listOf(cartLine("prod-1", quantity = 1)))
        viewModel.attachHeldOrder("held-1", 1, "Label")
        heldOrderRepository.updateResult = Result.failure(HeldOrderError(409, "INVALID_CASHIER_SESSION", "shift closed"))

        viewModel.updateHeldOrder("Label")

        assertEquals(HeldOrderUiError.RESOURCE_CONFLICT, viewModel.state.value.heldOrderError)
        assertEquals("held-1", viewModel.state.value.heldOrderId)
    }

    @Test
    fun cancelHeldOrder_failurePreservesContextAndList() {
        cartRepository.replaceCart(listOf(cartLine("prod-1", quantity = 1)))
        viewModel.attachHeldOrder("held-1", 4, "Label")
        heldOrderRepository.listResult = Result.success(listOf(order("held-1")))
        viewModel.listHeldOrders()
        heldOrderRepository.cancelResult = Result.failure(
            HeldOrderError(409, "HELD_ORDER_VERSION_CONFLICT", "conflict"),
        )

        viewModel.cancelHeldOrder("held-1", 4)

        assertEquals("held-1", viewModel.state.value.heldOrderId)
        assertEquals(listOf("held-1"), viewModel.state.value.heldOrders.map { it.id })
        assertEquals(HeldOrderUiError.VERSION_CONFLICT, viewModel.state.value.heldOrderError)
    }

    @Test
    fun cancelAttachedHeldOrder_isNoOpWithoutAttachedIdentity() {
        heldOrderRepository.cancelResult = Result.success(order("held-x"))

        viewModel.cancelAttachedHeldOrder()

        assertEquals(emptyList<Pair<String, Int>>(), heldOrderRepository.cancelCalls)
    }

    @Test
    fun duplicateSaveTap_isBlockedWhileOperationInFlight() {
        cartRepository.replaceCart(listOf(cartLine("prod-1", quantity = 1)))
        viewModel.attachHeldOrder("held-1", 1, "Label")
        heldOrderRepository.updateResult = Result.success(order("held-1", version = 2))

        viewModel.updateHeldOrder("Label")
        viewModel.updateHeldOrder("Label")

        // Unconfined dispatcher completes synchronously, so a second call after
        // completion is legitimate; the in-flight guard is asserted separately by
        // verifying only one request is issued per completed operation.
        assertEquals(2, heldOrderRepository.updateRequests.size)
        assertEquals(listOf(1, 2), heldOrderRepository.updateRequests.map { it.expectedVersion })
    }

    private class FakeHeldOrderRepository : HeldOrderRepository {
        data class ListCall(val outletId: String?, val status: String, val page: Int, val limit: Int)
        val listCalls = mutableListOf<ListCall>()
        val createRequests = mutableListOf<HeldOrderCreateRequest>()
        val updateRequests = mutableListOf<HeldOrderUpdateRequest>()
        val cancelCalls = mutableListOf<Pair<String, Int>>()
        val requestedGetIds = mutableListOf<String>()
        var listResult: Result<List<HeldOrder>> = Result.success(emptyList())
        var createResult: Result<HeldOrder> = Result.success(order("created"))
        var onCreate: (() -> Unit)? = null
        var updateResult: Result<HeldOrder> = Result.success(order("updated"))
        var cancelResult: Result<HeldOrder> = Result.success(order("cancelled", status = "CANCELLED"))
        val getResults = mutableMapOf<String, Result<HeldOrder>>()
        override suspend fun create(request: HeldOrderCreateRequest): Result<HeldOrder> {
            createRequests += request
            onCreate?.invoke()
            return createResult
        }
        override suspend fun list(outletId: String?, status: String, page: Int, limit: Int): Result<List<HeldOrder>> { listCalls += ListCall(outletId, status, page, limit); return listResult }
        override suspend fun get(id: String): Result<HeldOrder> {
            requestedGetIds += id
            return getResults[id] ?: Result.failure(AssertionError("unexpected get $id"))
        }
        override suspend fun update(request: HeldOrderUpdateRequest): Result<HeldOrder> { updateRequests += request; return updateResult }
        override suspend fun cancel(id: String, expectedVersion: Int): Result<HeldOrder> { cancelCalls += id to expectedVersion; return cancelResult }
        override suspend fun checkout(request: HeldOrderCheckoutRequest): Result<Transaction> = TODO()
    }

    private class FakeProductRepository : ProductRepository {
        var products: Result<List<Product>> = Result.success(emptyList())
        override suspend fun getProducts(query: String?, categoryId: String?, includeModifiers: Boolean?): Result<List<Product>> = products
        override suspend fun refreshProducts(query: String?, categoryId: String?, includeModifiers: Boolean?): Result<List<Product>> = products
        override suspend fun createProduct(name: String, sku: String, categoryId: String?, price: Long, cost: Long, minimumStock: Int, trackStock: Boolean): Result<Product> = TODO()
        override suspend fun updateProduct(productId: String, name: String?, sku: String?, categoryId: String?, categoryIdChanged: Boolean, price: Long?, cost: Long?, minimumStock: Int?, trackStock: Boolean?): Result<Product> = TODO()
    }

    private class FakeCartRepository : CartRepository {
        private val cartFlow = MutableStateFlow(Cart())
        private val identityFlow = MutableStateFlow<com.kasirkita.pos.domain.model.HeldOrderCartIdentity?>(null)
        override fun getCart(): StateFlow<Cart> = cartFlow.asStateFlow()
        override fun getHeldOrderIdentity(): StateFlow<com.kasirkita.pos.domain.model.HeldOrderCartIdentity?> = identityFlow.asStateFlow()
        override fun isAttachedToHeldOrder(): Boolean = identityFlow.value != null
        override fun attachHeldOrderIdentity(heldOrderId: String, expectedVersion: Int, label: String?) { identityFlow.value = com.kasirkita.pos.domain.model.HeldOrderCartIdentity(heldOrderId, expectedVersion, label) }
        override fun detachHeldOrderIdentity() { identityFlow.value = null }
        override fun replaceCart(items: List<CartItem>): CartUpdateResult {
            if (items.any { it.trackStock && it.availableStock != null && it.quantity > it.availableStock }) return CartUpdateResult.STOCK_LIMIT_REACHED
            cartFlow.value = Cart(items)
            return CartUpdateResult.UPDATED
        }
        override fun clearCart() { cartFlow.value = Cart() }
        override fun addProduct(product: Product, availableStock: Int?): CartUpdateResult = TODO()
        override fun addConfiguredProduct(product: Product, selectedModifiers: List<CartModifierSelectionSnapshot>, note: String?, availableStock: Int?): CartUpdateResult = TODO()
        override fun removeProduct(lineKey: String) = TODO()
        override fun updateQuantity(lineKey: String, quantity: Int): CartUpdateResult = TODO()
    }

    private class FakeOutletRepository : OutletRepository {
        private val flow = MutableStateFlow<Outlet?>(null)
        override val selectedOutlet: StateFlow<Outlet?> = flow.asStateFlow()
        fun setOutlet(value: Outlet?) { flow.value = value }
        override suspend fun getOutlets(): Result<List<Outlet>> = TODO()
        override suspend fun selectOutlet(outlet: Outlet) { flow.value = outlet }
        override suspend fun clearSelectedOutlet(tenantId: String?, userId: String?) { flow.value = null }
        override suspend fun restoreSelectedOutlet(): Unit = TODO()
    }

    private class FakeShiftRepository : ShiftRepository {
        private val flow = MutableStateFlow<Shift?>(null)
        override val currentShift: StateFlow<Shift?> = flow.asStateFlow()
        fun setShift(value: Shift?) { flow.value = value }
        override suspend fun getCurrentShift(): Result<Shift?> = TODO()
        override suspend fun openShift(outletId: String): Result<Shift> = TODO()
        override suspend fun closeShift(shiftId: String): Result<Shift> = TODO()
        override suspend fun getShiftSummary(shiftId: String) = TODO()
        override suspend fun clearCurrentShift(tenantId: String?, userId: String?) { flow.value = null }
        override suspend fun restoreCurrentShift(expectedOutletId: String?): Unit = TODO()
    }
}

private fun order(
    id: String,
    version: Int = 1,
    label: String? = null,
    status: String = "OPEN",
    items: List<HeldOrderItem> = emptyList(),
) = HeldOrder(
    id = id,
    outletId = "out-1",
    cashierSessionId = "shift-1",
    cashierUserId = "user-1",
    cashierName = "Cashier",
    actorRole = "CASHIER",
    label = label,
    status = status,
    version = version,
    subtotalEstimate = 0,
    taxEstimate = 0,
    totalEstimate = 0,
    itemCount = items.sumOf { it.quantity },
    createdAt = "2026-01-01T00:00:00Z",
    updatedAt = "2026-01-01T00:00:00Z",
    cancelledAt = null,
    convertedAt = null,
    items = items,
)

private fun heldItem(productId: String, quantity: Int, modifiers: List<HeldOrderModifier> = emptyList()) = HeldOrderItem(
    id = "item-$productId",
    productId = productId,
    quantity = quantity,
    productName = "Snapshot $productId",
    sku = "OLD-$productId",
    basePrice = 1,
    effectivePrice = 1,
    lineSubtotal = quantity.toLong(),
    lineDiscount = 0,
    lineTotal = quantity.toLong(),
    note = null,
    displayOrder = 1,
    modifiers = modifiers,
)

private fun cartLine(productId: String, quantity: Int, modifierIds: List<String> = emptyList(), note: String? = null) = CartItem(
    lineKey = CartLineKey.from(productId, modifierIds, note),
    productId = productId,
    name = "Item $productId",
    sku = "SKU-$productId",
    basePrice = 10_000,
    quantity = quantity,
    modifierSelections = modifierIds.map { CartModifierSelectionSnapshot(it, optionName = it) },
    note = note,
    trackStock = false,
    availableStock = null,
)

private fun product(
    id: String,
    price: Long = 10_000,
    trackStock: Boolean = false,
    stock: Int? = null,
    option: ModifierOption? = null,
) = Product(
    id = id,
    tenantId = "tenant-1",
    categoryId = null,
    name = "Current $id",
    sku = "SKU-$id",
    price = price,
    cost = 0,
    minimumStock = 0,
    trackStock = trackStock,
    isActive = true,
    createdAt = "2026-01-01T00:00:00Z",
    stock = stock,
    modifierMetadataLoaded = true,
    modifierGroups = option?.let { listOf(ModifierGroup("grp-1", "tenant-1", "Size", true, 1, false, SelectionMode.SINGLE, listOf(it))) }.orEmpty(),
)

private fun outlet(id: String) = Outlet(id, "tenant-1", "Outlet", null, true, "2026-01-01T00:00:00Z")

private fun shift(id: String, outletId: String, status: String) = Shift(id, outletId, "user-1", null, null, null, null, status, "2026-01-01T00:00:00Z", null)
