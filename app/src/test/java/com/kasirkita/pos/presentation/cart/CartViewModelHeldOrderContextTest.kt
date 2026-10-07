package com.kasirkita.pos.presentation.cart

import androidx.lifecycle.SavedStateHandle
import com.kasirkita.pos.domain.model.Cart
import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.CartLineKey
import com.kasirkita.pos.domain.model.CartModifierSelectionSnapshot
import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.model.Shift
import com.kasirkita.pos.domain.repository.CartRepository
import com.kasirkita.pos.domain.repository.CartUpdateResult
import com.kasirkita.pos.domain.repository.OutletRepository
import com.kasirkita.pos.domain.repository.ShiftRepository
import com.kasirkita.pos.domain.usecase.AddToCartUseCase
import com.kasirkita.pos.domain.usecase.RemoveFromCartUseCase
import com.kasirkita.pos.domain.usecase.UpdateCartQuantityUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CartViewModelHeldOrderContextTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var cartRepository: FakeCartRepository
    private lateinit var outletRepository: FakeOutletRepository
    private lateinit var shiftRepository: FakeShiftRepository
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: CartViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        cartRepository = FakeCartRepository()
        outletRepository = FakeOutletRepository()
        shiftRepository = FakeShiftRepository()
        savedStateHandle = SavedStateHandle()
        viewModel = newViewModel(cartRepository, outletRepository, shiftRepository, savedStateHandle)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(
        cartRepo: CartRepository,
        outlets: OutletRepository,
        shifts: ShiftRepository,
        handle: SavedStateHandle,
    ) = CartViewModel(
        cartRepository = cartRepo,
        heldOrderRepository = FakeHeldOrderRepository(),
        productRepository = FakeProductRepository(),
        addToCart = AddToCartUseCase(cartRepo),
        removeFromCart = RemoveFromCartUseCase(cartRepo),
        updateCartQuantity = UpdateCartQuantityUseCase(cartRepo),
        outletRepository = outlets,
        shiftRepository = shifts,
        savedStateHandle = handle,
    )

    private class FakeHeldOrderRepository : com.kasirkita.pos.domain.repository.HeldOrderRepository {
        override suspend fun create(request: com.kasirkita.pos.domain.model.HeldOrderCreateRequest): Result<com.kasirkita.pos.domain.model.HeldOrder> = TODO()
        override suspend fun list(outletId: String?, status: String, page: Int, limit: Int): Result<List<com.kasirkita.pos.domain.model.HeldOrder>> = Result.success(emptyList())
        override suspend fun get(id: String): Result<com.kasirkita.pos.domain.model.HeldOrder> = Result.failure(java.io.IOException("offline"))
        override suspend fun update(request: com.kasirkita.pos.domain.model.HeldOrderUpdateRequest): Result<com.kasirkita.pos.domain.model.HeldOrder> = TODO()
        override suspend fun cancel(id: String, expectedVersion: Int): Result<com.kasirkita.pos.domain.model.HeldOrder> = TODO()
        override suspend fun checkout(request: com.kasirkita.pos.domain.model.HeldOrderCheckoutRequest): Result<com.kasirkita.pos.domain.model.HeldOrderCheckoutResult> = TODO()
    }

    private class FakeProductRepository : com.kasirkita.pos.domain.repository.ProductRepository {
        override suspend fun getProducts(query: String?, categoryId: String?, includeModifiers: Boolean?): Result<List<com.kasirkita.pos.domain.model.Product>> = Result.success(emptyList())
        override suspend fun refreshProducts(query: String?, categoryId: String?, includeModifiers: Boolean?): Result<List<com.kasirkita.pos.domain.model.Product>> = Result.success(emptyList())
        override suspend fun createProduct(name: String, sku: String, categoryId: String?, price: Long, cost: Long, minimumStock: Int, trackStock: Boolean): Result<com.kasirkita.pos.domain.model.Product> = TODO()
        override suspend fun updateProduct(productId: String, name: String?, sku: String?, categoryId: String?, categoryIdChanged: Boolean, price: Long?, cost: Long?, minimumStock: Int?, trackStock: Boolean?): Result<com.kasirkita.pos.domain.model.Product> = TODO()
    }

    // -------------------------------------------------------------------
    // Held Order editing context
    // -------------------------------------------------------------------

    @Test
    fun attachHeldOrder_setsEditingContextInState() {
        viewModel.attachHeldOrder("held-123", 5, "Order A")

        val state = viewModel.state.value
        assertTrue(state.isEditingHeldOrder)
        assertEquals("held-123", state.heldOrderId)
        assertEquals(5, state.heldOrderExpectedVersion)
        assertEquals("Order A", state.heldOrderLabel)
    }

    @Test
    fun detachHeldOrder_clearsEditingContextButPreservesCart() {
        cartRepository.seedWith(line("coffee", 3))

        viewModel.attachHeldOrder("held-123", 2, "Order")
        viewModel.detachHeldOrder()

        val state = viewModel.state.value
        assertFalse(state.isEditingHeldOrder)
        assertNull(state.heldOrderId)
        assertNull(state.heldOrderExpectedVersion)
        assertNull(state.heldOrderLabel)
        // Cart items MUST survive cancel of the attached Held Order.
        assertEquals(3, state.cart.items.single().quantity)
        assertEquals("coffee", state.cart.items.single().productId)
    }

    @Test
    fun adoptHeldOrderVersion_updatesExpectedVersionOnly() {
        viewModel.attachHeldOrder("held-123", 1, "Label")

        viewModel.adoptHeldOrderVersion(2)

        val state = viewModel.state.value
        assertEquals("held-123", state.heldOrderId)
        assertEquals(2, state.heldOrderExpectedVersion)
        assertEquals("Label", state.heldOrderLabel)
    }

    @Test
    fun adoptHeldOrderVersion_whenNoHeldOrderAttached_isNoOp() {
        viewModel.adoptHeldOrderVersion(99)

        val state = viewModel.state.value
        assertFalse(state.isEditingHeldOrder)
        assertNull(state.heldOrderId)
        assertNull(state.heldOrderExpectedVersion)
    }

    @Test
    fun cartQuantityChange_doesNotDetachHeldOrder() {
        cartRepository.seedWith(line("coffee", 2))
        viewModel.attachHeldOrder("held-99", 3, "Lbl")

        viewModel.increaseQuantity(lineKey("coffee"))

        val state = viewModel.state.value
        assertTrue("quantity change must not detach the Held Order", state.isEditingHeldOrder)
        assertEquals("held-99", state.heldOrderId)
        assertEquals(3, state.heldOrderExpectedVersion)
        assertEquals(3, state.cart.items.single().quantity)
    }

    @Test
    fun removeProduct_doesNotDetachHeldOrder() {
        cartRepository.seedWith(line("coffee", 1))
        viewModel.attachHeldOrder("held-x", 4, "X")

        viewModel.removeProduct(lineKey("coffee"))

        val state = viewModel.state.value
        assertTrue("removal must not detach the Held Order", state.isEditingHeldOrder)
        assertEquals("held-x", state.heldOrderId)
        assertTrue(state.cart.items.isEmpty())
    }

    @Test
    fun atomicCartReplacement_doesNotDetachHeldOrder() {
        cartRepository.seedWith(line("coffee", 1))
        viewModel.attachHeldOrder("held-r", 1, "R")

        cartRepository.emitReplacement(listOf(line("tea", 4)))

        val state = viewModel.state.value
        assertTrue(state.isEditingHeldOrder)
        assertEquals("held-r", state.heldOrderId)
        assertEquals(4, state.cart.items.single().quantity)
        assertEquals("tea", state.cart.items.single().productId)
    }

    // -------------------------------------------------------------------
    // Process death
    // -------------------------------------------------------------------

    @Test
    fun processDeath_recreatesHeldOrderContextFromSavedStateHandle() {
        val handle = SavedStateHandle()
        HeldOrderCartContext(handle).attach("held-survive", 7, "Survive")

        val recreated = newViewModel(
            FakeCartRepository(), FakeOutletRepository(), FakeShiftRepository(), handle,
        )

        val state = recreated.state.value
        assertTrue(state.isEditingHeldOrder)
        assertEquals("held-survive", state.heldOrderId)
        assertEquals(7, state.heldOrderExpectedVersion)
        assertEquals("Survive", state.heldOrderLabel)
    }

    @Test
    fun processDeath_withEmptySavedStateHandle_yieldsNormalCart() {
        val recreated = newViewModel(
            FakeCartRepository(), FakeOutletRepository(), FakeShiftRepository(), SavedStateHandle(),
        )

        val state = recreated.state.value
        assertFalse(state.isEditingHeldOrder)
        assertNull(state.heldOrderId)
        assertNull(state.heldOrderExpectedVersion)
        assertNull(state.heldOrderLabel)
    }

    @Test
    fun savedStateHandle_persistsOnlyLightweightIdentityKeys() {
        val handle = SavedStateHandle()
        val context = HeldOrderCartContext(handle)
        context.attach("held-k", 4, "Key")

        assertEquals(
            setOf(
                HeldOrderCartContext.KEY_HELD_ORDER_ID,
                HeldOrderCartContext.KEY_EXPECTED_VERSION,
                HeldOrderCartContext.KEY_LABEL,
            ),
            handle.keys(),
        )
    }

    @Test
    fun savedStateHandle_blankHeldOrderId_isNotTreatedAsEditing() {
        val handle = SavedStateHandle()
        val context = HeldOrderCartContext(handle)
        context.attach("   ", 1, "x")

        assertFalse(context.isEditing)
        assertNull(context.heldOrderId)
        assertEquals(
            setOf(HeldOrderCartContext.KEY_EXPECTED_VERSION, HeldOrderCartContext.KEY_LABEL),
            handle.keys(),
        )
    }

    // -------------------------------------------------------------------
    // Operational context
    // -------------------------------------------------------------------

    @Test
    fun resolveOperationalContext_usesOutletIdAndShiftId() {
        outletRepository.setOutlet(outlet("out-1"))
        shiftRepository.setShift(shift("shift-1", outletId = "out-1", status = "OPEN"))

        val context = viewModel.resolveOperationalContext().getOrThrow()

        assertEquals("out-1", context.outletId)
        assertEquals("shift-1", context.cashierSessionId)
    }

    @Test
    fun resolveOperationalContext_acceptsLowercaseOpenStatus() {
        outletRepository.setOutlet(outlet("out-1"))
        shiftRepository.setShift(shift("shift-1", outletId = "out-1", status = "open"))

        assertTrue(viewModel.resolveOperationalContext().isSuccess)
    }

    @Test
    fun resolveOperationalContext_failsWhenNoOutletSelected() {
        shiftRepository.setShift(shift("shift-1", outletId = "out-1", status = "OPEN"))

        val result = viewModel.resolveOperationalContext()

        assertTrue(result.isFailure)
        assertEquals(
            OperationalContextRejection.NO_OUTLET,
            (result.exceptionOrNull() as OperationalContextUnavailableException).rejection,
        )
    }

    @Test
    fun resolveOperationalContext_failsWhenNoShiftOpen() {
        outletRepository.setOutlet(outlet("out-1"))

        val result = viewModel.resolveOperationalContext()

        assertTrue(result.isFailure)
        assertEquals(
            OperationalContextRejection.NO_OPEN_SHIFT,
            (result.exceptionOrNull() as OperationalContextUnavailableException).rejection,
        )
    }

    @Test
    fun resolveOperationalContext_failsWhenShiftClosed() {
        outletRepository.setOutlet(outlet("out-1"))
        shiftRepository.setShift(shift("shift-1", outletId = "out-1", status = "CLOSED"))

        val result = viewModel.resolveOperationalContext()

        assertTrue(result.isFailure)
        assertEquals(
            OperationalContextRejection.NO_OPEN_SHIFT,
            (result.exceptionOrNull() as OperationalContextUnavailableException).rejection,
        )
    }

    @Test
    fun resolveOperationalContext_failsWhenShiftBelongsToDifferentOutlet() {
        outletRepository.setOutlet(outlet("out-1"))
        shiftRepository.setShift(shift("shift-1", outletId = "out-other", status = "OPEN"))

        val result = viewModel.resolveOperationalContext()

        assertTrue(result.isFailure)
        assertEquals(
            OperationalContextRejection.SHIFT_OUTLET_MISMATCH,
            (result.exceptionOrNull() as OperationalContextUnavailableException).rejection,
        )
    }

    @Test
    fun resolveOperationalContext_neverReturnsPlaceholderIds() {
        outletRepository.setOutlet(outlet("out-real"))
        shiftRepository.setShift(shift("shift-real", outletId = "out-real", status = "OPEN"))

        val context = viewModel.resolveOperationalContext().getOrThrow()

        listOf(context.outletId, context.cashierSessionId).forEach { id ->
            assertTrue("id must not be blank: '$id'", id.isNotBlank())
            assertTrue("id must be authoritative, not placeholder: '$id'", !id.contains("placeholder", true))
        }
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private fun outlet(id: String) = Outlet(
        id = id,
        tenantId = "tenant-1",
        name = "Outlet $id",
        address = null,
        isActive = true,
        createdAt = "2026-01-01T00:00:00Z",
    )

    private fun shift(id: String, outletId: String, status: String) = Shift(
        id = id,
        outletId = outletId,
        userId = "user-1",
        openingCash = null,
        closingCash = null,
        expectedCash = null,
        difference = null,
        status = status,
        openedAt = "2026-01-01T00:00:00Z",
        closedAt = null,
    )

    private fun line(productId: String, quantity: Int) = CartItem(
        lineKey = CartLineKey.from(productId, emptyList(), null),
        productId = productId,
        name = "Item $productId",
        sku = "SKU-$productId",
        basePrice = 20_000L,
        quantity = quantity,
        modifierSelections = emptyList(),
        note = null,
        trackStock = false,
        availableStock = null,
    )

    private fun lineKey(productId: String) = CartLineKey.from(productId, emptyList(), null).value

    private class FakeCartRepository : CartRepository {
        private val flow = MutableStateFlow(Cart())
        private val identityFlow = MutableStateFlow<com.kasirkita.pos.domain.model.HeldOrderCartIdentity?>(null)
        override fun getCart(): StateFlow<Cart> = flow.asStateFlow()
        override fun getHeldOrderIdentity(): StateFlow<com.kasirkita.pos.domain.model.HeldOrderCartIdentity?> = identityFlow.asStateFlow()
        override fun isAttachedToHeldOrder(): Boolean = identityFlow.value != null
        override fun attachHeldOrderIdentity(heldOrderId: String, expectedVersion: Int, label: String?) {
            identityFlow.value = com.kasirkita.pos.domain.model.HeldOrderCartIdentity(heldOrderId, expectedVersion, label)
        }
        override fun detachHeldOrderIdentity() { identityFlow.value = null }

        fun seedWith(item: CartItem) { flow.value = Cart(listOf(item)) }

        fun emitReplacement(items: List<CartItem>) { flow.value = Cart(items) }

        override fun addProduct(product: Product, availableStock: Int?): CartUpdateResult =
            addConfiguredProduct(product, emptyList(), null, availableStock)

        override fun addConfiguredProduct(
            product: Product,
            selectedModifiers: List<CartModifierSelectionSnapshot>,
            note: String?,
            availableStock: Int?,
        ): CartUpdateResult {
            val key = CartLineKey.from(product.id, selectedModifiers.map { it.optionId }, note)
            val existing = flow.value.items.firstOrNull { it.lineKey == key }
            val item = CartItem(
                lineKey = key,
                productId = product.id,
                name = product.name,
                sku = product.sku,
                basePrice = product.price,
                quantity = (existing?.quantity ?: 0) + 1,
                modifierSelections = selectedModifiers,
                note = note,
                trackStock = product.trackStock,
                availableStock = availableStock,
            )
            flow.value = if (existing == null) {
                flow.value.copy(items = flow.value.items + item)
            } else {
                flow.value.copy(items = flow.value.items.map { if (it.lineKey == key) item else it })
            }
            return CartUpdateResult.UPDATED
        }

        override fun removeProduct(lineKey: String) {
            flow.value = flow.value.copy(items = flow.value.items.filterNot { it.lineKey.value == lineKey })
        }

        override fun updateQuantity(lineKey: String, quantity: Int): CartUpdateResult {
            val existing = flow.value.items.find { it.lineKey.value == lineKey }
            if (existing != null && existing.trackStock && existing.availableStock != null &&
                quantity > existing.availableStock
            ) {
                return CartUpdateResult.STOCK_LIMIT_REACHED
            }
            flow.value = flow.value.copy(items = flow.value.items.map {
                if (it.lineKey.value == lineKey) it.copy(quantity = quantity) else it
            })
            return CartUpdateResult.UPDATED
        }

        override fun replaceCart(items: List<CartItem>): CartUpdateResult {
            for (item in items) {
                if (item.trackStock && item.availableStock != null && item.quantity > item.availableStock) {
                    return CartUpdateResult.STOCK_LIMIT_REACHED
                }
            }
            flow.value = Cart(items)
            return CartUpdateResult.UPDATED
        }

        override fun clearCart() { flow.value = Cart() }
    }

    private class FakeOutletRepository : OutletRepository {
        private val flow = MutableStateFlow<Outlet?>(null)
        override val selectedOutlet: StateFlow<Outlet?> = flow.asStateFlow()
        fun setOutlet(value: Outlet?) { flow.value = value }
        override suspend fun getOutlets() = TODO()
        override suspend fun selectOutlet(outlet: Outlet) { flow.value = outlet }
        override suspend fun clearSelectedOutlet(tenantId: String?, userId: String?) { flow.value = null }
        override suspend fun restoreSelectedOutlet() = TODO()
    }

    private class FakeShiftRepository : ShiftRepository {
        private val flow = MutableStateFlow<Shift?>(null)
        override val currentShift: StateFlow<Shift?> = flow.asStateFlow()
        fun setShift(value: Shift?) { flow.value = value }
        override suspend fun getCurrentShift() = TODO()
        override suspend fun openShift(outletId: String) = TODO()
        override suspend fun closeShift(shiftId: String) = TODO()
        override suspend fun getShiftSummary(shiftId: String) = TODO()
        override suspend fun clearCurrentShift(tenantId: String?, userId: String?) { flow.value = null }
        override suspend fun restoreCurrentShift(expectedOutletId: String?) = TODO()
    }
}