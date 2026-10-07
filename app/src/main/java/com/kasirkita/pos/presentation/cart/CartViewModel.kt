package com.kasirkita.pos.presentation.cart

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.error.HeldOrderError
import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.HeldOrder
import com.kasirkita.pos.domain.model.HeldOrderCreateRequest
import com.kasirkita.pos.domain.model.HeldOrderItemRequest
import com.kasirkita.pos.domain.model.HeldOrderUpdateRequest
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.repository.CartRepository
import com.kasirkita.pos.domain.repository.CartUpdateResult
import com.kasirkita.pos.domain.repository.HeldOrderRepository
import com.kasirkita.pos.domain.repository.OutletRepository
import com.kasirkita.pos.domain.repository.ProductRepository
import com.kasirkita.pos.domain.repository.ShiftRepository
import com.kasirkita.pos.domain.usecase.AddToCartUseCase
import com.kasirkita.pos.domain.usecase.RemoveFromCartUseCase
import com.kasirkita.pos.domain.usecase.UpdateCartQuantityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CartViewModel @Inject constructor(
    private val cartRepository: CartRepository,
    private val heldOrderRepository: HeldOrderRepository,
    private val productRepository: ProductRepository,
    private val addToCart: AddToCartUseCase,
    private val removeFromCart: RemoveFromCartUseCase,
    private val updateCartQuantity: UpdateCartQuantityUseCase,
    outletRepository: OutletRepository,
    shiftRepository: ShiftRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(CartState())
    val state: StateFlow<CartState> = _state.asStateFlow()

    /**
     * Lightweight Held Order editing identity, persisted across process death.
     *
     * Only [HeldOrderCartContext.heldOrderId], [HeldOrderCartContext.expectedVersion],
     * [HeldOrderCartContext.label] and the editing flag are stored in [SavedStateHandle].
     * Held Order *contents* are never serialized there: on recreation the
     * authoritative detail is refetched from the backend and the working cart is
     * rebuilt only after that succeeds.
     */
    private val heldOrderContext = HeldOrderCartContext(savedStateHandle)

    init {
        heldOrderContext.heldOrderId?.let { id ->
            cartRepository.attachHeldOrderIdentity(id, heldOrderContext.expectedVersion ?: 0, heldOrderContext.label)
            // Process recreation keeps only identity. Refetch authoritative detail;
            // never silently downgrade the existing cart into a normal checkout cart.
            restoreHeldOrder(id)
        }

        viewModelScope.launch {
            // Merge cart emissions with the held-order editing context rather than
            // replacing the whole state: any cart change (quantity change, removal,
            // atomic replacement) must never silently detach the Held Order identity
            // from the working cart.
            combine(
                cartRepository.getCart(),
                outletRepository.selectedOutlet,
                shiftRepository.currentShift,
                cartRepository.getHeldOrderIdentity(),
            ) { cart, outlet, shift, identity ->
                Quadruple(cart, outlet, shift, identity)
            }
                .collect { (cart, outlet, shift, identity) ->
                    if (identity != null && heldOrderContext.heldOrderId != identity.heldOrderId) {
                        heldOrderContext.attach(identity.heldOrderId, identity.expectedVersion, identity.label)
                    } else if (identity == null && _state.value.isEditingHeldOrder) {
                        heldOrderContext.clear()
                    }
                    _state.update { current ->
                        val outletChanged = current.selectedOutlet?.id != outlet?.id
                        current.copy(
                            cart = cart,
                            selectedOutlet = outlet,
                            currentShift = shift,
                            isEditingHeldOrder = identity != null,
                            heldOrderId = identity?.heldOrderId ?: heldOrderContext.heldOrderId,
                            heldOrderExpectedVersion = identity?.expectedVersion ?: heldOrderContext.expectedVersion,
                            heldOrderLabel = identity?.label ?: heldOrderContext.label,
                            heldOrders = if (outletChanged) emptyList() else current.heldOrders,
                            heldOrdersLoaded = if (outletChanged) false else current.heldOrdersLoaded,
                            heldOrdersOutletId = if (outletChanged) null else current.heldOrdersOutletId,
                        )
                    }
                }
        }
    }

    fun increaseQuantity(lineKey: String) {
        val item = _state.value.cart.items.find { it.lineKey.value == lineKey } ?: return
        handleUpdate(updateCartQuantity(lineKey, item.quantity + 1))
    }

    fun decreaseQuantity(lineKey: String) {
        val item = _state.value.cart.items.find { it.lineKey.value == lineKey } ?: return
        handleUpdate(updateCartQuantity(lineKey, item.quantity - 1))
    }

    fun removeProduct(lineKey: String) {
        removeFromCart(lineKey)
    }

    // -------------------------------------------------------------------
    // Held Order editing context
    // -------------------------------------------------------------------

    fun attachHeldOrder(heldOrderId: String, expectedVersion: Int, label: String?) {
        heldOrderContext.attach(heldOrderId = heldOrderId, expectedVersion = expectedVersion, label = label)
        cartRepository.attachHeldOrderIdentity(heldOrderId, expectedVersion, label)
        publishContext()
    }

    /**
     * Detaches the Held Order identity while PRESERVING the cart contents.
     *
     * Used when the user explicitly cancels the Held Order currently attached to
     * the cart: the items remain and become a normal local cart, so no local work
     * is destroyed.
     */
    fun detachHeldOrder() {
        heldOrderContext.clear()
        cartRepository.detachHeldOrderIdentity()
        publishContext()
    }

    /** Advances the expected version from a successful server response only. */
    fun adoptHeldOrderVersion(expectedVersion: Int) {
        val currentId = heldOrderContext.heldOrderId ?: return
        heldOrderContext.attach(
            heldOrderId = currentId,
            expectedVersion = expectedVersion,
            label = heldOrderContext.label,
        )
        cartRepository.attachHeldOrderIdentity(currentId, expectedVersion, heldOrderContext.label)
        publishContext()
    }

    private fun publishContext() {
        val identity = cartRepository.getHeldOrderIdentity().value
        _state.update { current ->
            current.copy(
                isEditingHeldOrder = identity != null,
                heldOrderId = identity?.heldOrderId ?: heldOrderContext.heldOrderId,
                heldOrderExpectedVersion = identity?.expectedVersion ?: heldOrderContext.expectedVersion,
                heldOrderLabel = identity?.label ?: heldOrderContext.label,
            )
        }
    }

    /**
     * Resolves the authoritative operational context required for any Held Order
     * write, reusing the same source and the same validation as the normal checkout
     * flow. Never returns placeholder or empty IDs.
     */
    fun resolveOperationalContext(): Result<OperationalContext> {
        val outlet = _state.value.selectedOutlet
        val shift = _state.value.currentShift

        val rejection = when {
            outlet == null -> OperationalContextRejection.NO_OUTLET
            shift == null || !shift.status.equals(OPEN_SHIFT_STATUS, ignoreCase = true) ->
                OperationalContextRejection.NO_OPEN_SHIFT
            shift.outletId != outlet.id -> OperationalContextRejection.SHIFT_OUTLET_MISMATCH
            else -> null
        }
        rejection?.let { return Result.failure(OperationalContextUnavailableException(it)) }

        return Result.success(
            OperationalContext(
                outletId = requireNotNull(outlet).id,
                cashierSessionId = requireNotNull(shift).id,
            ),
        )
    }

    fun clearHeldOrderError() {
        _state.update { it.copy(heldOrderError = null) }
    }

    fun listHeldOrders(page: Int = 1, limit: Int = 20) {
        val context = resolveOperationalContext().getOrNull() ?: run {
            _state.update { it.copy(heldOrderError = contextError()) }
            return
        }
        if (_state.value.heldOrderOperation != null) return
        viewModelScope.launch {
            _state.update { it.copy(heldOrderOperation = HeldOrderOperation.LIST, heldOrderError = null) }
            heldOrderRepository.list(context.outletId, "OPEN", page, limit)
                .onSuccess { orders -> publishHeldOrders(context.outletId, orders) }
                .onFailure { error -> _state.update { it.copy(heldOrderError = mapHeldOrderError(error)) } }
            _state.update { it.copy(heldOrderOperation = null) }
        }
    }

    fun saveHeldOrder(label: String?) {
        if (_state.value.cart.items.isEmpty()) {
            _state.update { it.copy(heldOrderError = HeldOrderUiError.EMPTY_CART) }
            return
        }
        if (_state.value.heldOrderId != null) {
            updateHeldOrder(label)
            return
        }
        val context = resolveOperationalContext().getOrNull() ?: run {
            _state.update { it.copy(heldOrderError = contextError()) }
            return
        }
        if (_state.value.heldOrderOperation != null) return
        val savedItems = _state.value.cart.items
        val requests = savedItems.map(::toHeldOrderItemRequest)
        viewModelScope.launch {
            _state.update { it.copy(heldOrderOperation = HeldOrderOperation.SAVE, heldOrderError = null) }
            heldOrderRepository.create(
                HeldOrderCreateRequest(context.outletId, context.cashierSessionId, normalizeLabel(label), requests),
            ).onSuccess {
                refreshHeldOrders(context.outletId)
                if (cartRepository.getCart().value.items == savedItems) {
                    cartRepository.clearCart()
                    detachHeldOrder()
                } else {
                    _state.update { it.copy(heldOrderError = HeldOrderUiError.CART_CHANGED) }
                }
            }.onFailure { error -> _state.update { it.copy(heldOrderError = mapHeldOrderError(error)) } }
            _state.update { it.copy(heldOrderOperation = null) }
        }
    }

    /**
     * Resolves a non-empty-cart collision without exposing an intermediate empty
     * cart as a completed user action. The current cart is cleared only after the
     * server accepts it, and the selected target is then fetched and restored.
     * If create fails, the current cart remains untouched and the target is never
     * fetched. If target restore fails, the newly saved cart remains recoverable
     * from the server and the error is surfaced instead of fabricating a cart.
     */
    fun saveCurrentCartThenRestoreHeldOrder(targetHeldOrderId: String, label: String?) {
        if (_state.value.cart.items.isEmpty()) {
            restoreHeldOrder(targetHeldOrderId)
            return
        }
        val context = resolveOperationalContext().getOrNull() ?: run {
            _state.update { it.copy(heldOrderError = contextError()) }
            return
        }
        if (_state.value.heldOrderOperation != null) return
        val savedItems = _state.value.cart.items
        val requests = savedItems.map(::toHeldOrderItemRequest)
        viewModelScope.launch {
            _state.update { it.copy(heldOrderOperation = HeldOrderOperation.SAVE, heldOrderError = null) }
            heldOrderRepository.create(
                HeldOrderCreateRequest(
                    context.outletId,
                    context.cashierSessionId,
                    normalizeLabel(label),
                    requests,
                ),
            ).fold(
                onSuccess = {
                    refreshHeldOrders(context.outletId)
                    if (cartRepository.getCart().value.items != savedItems) {
                        _state.update { it.copy(heldOrderError = HeldOrderUiError.CART_CHANGED) }
                    } else {
                        cartRepository.clearCart()
                        detachHeldOrder()
                        heldOrderRepository.get(targetHeldOrderId).fold(
                            onSuccess = { detail -> restoreDetail(detail) },
                            onFailure = { error ->
                                _state.update { it.copy(heldOrderError = mapHeldOrderError(error)) }
                            },
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(heldOrderError = mapHeldOrderError(error)) }
                },
            )
            _state.update { it.copy(heldOrderOperation = null) }
        }
    }

    fun updateHeldOrder(label: String? = heldOrderContext.label) {
        val id = heldOrderContext.heldOrderId
        val version = heldOrderContext.expectedVersion
        if (id == null || version == null || _state.value.cart.items.isEmpty()) {
            _state.update { it.copy(heldOrderError = HeldOrderUiError.EMPTY_CART) }
            return
        }
        if (resolveOperationalContext().isFailure) {
            _state.update { it.copy(heldOrderError = contextError()) }
            return
        }
        if (_state.value.heldOrderOperation != null) return
        viewModelScope.launch {
            _state.update { it.copy(heldOrderOperation = HeldOrderOperation.UPDATE, heldOrderError = null) }
            heldOrderRepository.update(
                HeldOrderUpdateRequest(id, version, normalizeLabel(label), _state.value.cart.items.map(::toHeldOrderItemRequest)),
            ).onSuccess { updated ->
                attachHeldOrder(updated.id, updated.version, updated.label)
                revalidateHeldOrders()
            }.onFailure { error -> _state.update { it.copy(heldOrderError = mapHeldOrderError(error)) } }
            _state.update { it.copy(heldOrderOperation = null) }
        }
    }

    fun cancelAttachedHeldOrder() {
        val id = heldOrderContext.heldOrderId ?: return
        val expectedVersion = heldOrderContext.expectedVersion ?: return
        cancelHeldOrder(id, expectedVersion)
    }

    fun cancelHeldOrder(id: String, expectedVersion: Int) {
        if (resolveOperationalContext().isFailure) {
            _state.update { it.copy(heldOrderError = contextError()) }
            return
        }
        if (_state.value.heldOrderOperation != null) return
        viewModelScope.launch {
            _state.update { it.copy(heldOrderOperation = HeldOrderOperation.CANCEL, heldOrderError = null) }
            heldOrderRepository.cancel(id, expectedVersion)
                .onSuccess {
                    revalidateHeldOrders()
                    if (heldOrderContext.heldOrderId == id) detachHeldOrder()
                }
                .onFailure { error -> _state.update { it.copy(heldOrderError = mapHeldOrderError(error)) } }
            _state.update { it.copy(heldOrderOperation = null) }
        }
    }

    fun restoreHeldOrder(id: String) {
        if (resolveOperationalContext().isFailure) {
            _state.update { it.copy(heldOrderError = contextError()) }
            return
        }
        if (_state.value.heldOrderOperation != null) return
        viewModelScope.launch {
            _state.update { it.copy(heldOrderOperation = HeldOrderOperation.RESTORE, heldOrderError = null) }
            val result = heldOrderRepository.get(id)
            result.fold(
                onSuccess = { detail -> restoreDetail(detail) },
                onFailure = { error -> _state.update { it.copy(heldOrderError = mapHeldOrderError(error)) } },
            )
            _state.update { it.copy(heldOrderOperation = null) }
        }
    }

    fun reloadAttachedHeldOrder() {
        val id = cartRepository.getHeldOrderIdentity().value?.heldOrderId ?: return
        restoreHeldOrder(id)
    }

    private suspend fun restoreDetail(detail: HeldOrder) {
        if (!detail.status.equals("OPEN", ignoreCase = true)) {
            _state.update { it.copy(heldOrderError = HeldOrderUiError.NOT_OPEN) }
            return
        }
        val context = resolveOperationalContext().getOrNull()
        if (context == null) {
            _state.update { it.copy(heldOrderError = contextError()) }
            return
        }
        if (detail.outletId != context.outletId || detail.cashierSessionId != context.cashierSessionId) {
            _state.update { it.copy(heldOrderError = HeldOrderUiError.RESOURCE_CONFLICT) }
            return
        }
        val products = productRepository.getProducts(includeModifiers = true).getOrElse { error ->
            _state.update { it.copy(heldOrderError = mapHeldOrderError(error)) }
            return
        }
        val byId = products.filter { it.isActive }.associateBy { it.id }
        val candidate = mutableListOf<CartItem>()
        for (heldItem in detail.items) {
            val product = byId[heldItem.productId]
            if (product == null) {
                _state.update { it.copy(heldOrderError = HeldOrderUiError.CATALOG_CONFLICT) }
                return
            }
            val selections = mutableListOf<com.kasirkita.pos.domain.model.CartModifierSelectionSnapshot>()
            for (modifier in heldItem.modifiers) {
                val optionId = modifier.modifierOptionId
                if (optionId == null) {
                    _state.update { it.copy(heldOrderError = HeldOrderUiError.CATALOG_CONFLICT) }
                    return
                }
                val match = product.modifierGroups.asSequence()
                    .filter { it.isActive }
                    .flatMap { group -> group.options.filter { it.isActive }.map { group to it } }
                    .firstOrNull { (_, option) -> option.id == optionId }
                if (match == null) {
                    _state.update { it.copy(heldOrderError = HeldOrderUiError.CATALOG_CONFLICT) }
                    return
                }
                val (group, option) = match
                selections += com.kasirkita.pos.domain.model.CartModifierSelectionSnapshot(
                    option.id, group.id, group.name, option.name, option.priceDelta,
                )
            }
            candidate += CartItem(
                lineKey = com.kasirkita.pos.domain.model.CartLineKey.from(product.id, selections.map { it.optionId }, heldItem.note),
                productId = product.id,
                name = product.name,
                sku = product.sku,
                basePrice = product.price,
                quantity = heldItem.quantity,
                modifierSelections = selections,
                note = heldItem.note,
                trackStock = product.trackStock,
                availableStock = product.stock,
            )
        }
        val mergedCandidate = candidate.groupBy { it.lineKey }.values.map { sameLine ->
            sameLine.first().copy(quantity = sameLine.sumOf { it.quantity })
        }
        if (cartRepository.replaceCart(mergedCandidate) == CartUpdateResult.STOCK_LIMIT_REACHED) {
            _state.update { it.copy(heldOrderError = HeldOrderUiError.STOCK_CONFLICT) }
            return
        }
        attachHeldOrder(detail.id, detail.version, detail.label)
    }

    private fun revalidateHeldOrders() {
        val context = resolveOperationalContext().getOrNull() ?: return
        viewModelScope.launch {
            heldOrderRepository.list(context.outletId, "OPEN", 1, 20)
                .onSuccess { orders -> publishHeldOrders(context.outletId, orders) }
        }
    }

    private fun refreshHeldOrders(outletId: String) {
        viewModelScope.launch {
            heldOrderRepository.list(outletId, "OPEN", 1, 20)
                .onSuccess { orders -> publishHeldOrders(outletId, orders) }
        }
    }

    private fun publishHeldOrders(outletId: String, orders: List<HeldOrder>) {
        if (_state.value.selectedOutlet?.id != outletId) return
        _state.update {
            it.copy(
                heldOrders = orders,
                heldOrdersLoaded = true,
                heldOrdersOutletId = outletId,
            )
        }
    }

    private fun toHeldOrderItemRequest(item: CartItem) = HeldOrderItemRequest(
        productId = item.productId,
        quantity = item.quantity,
        modifierOptionIds = item.modifierOptionIds,
        note = item.note,
    )

    private fun normalizeLabel(label: String?): String? = label?.trim()?.takeIf { it.isNotEmpty() }

    private fun contextError(): HeldOrderUiError = when ((resolveOperationalContext().exceptionOrNull() as? OperationalContextUnavailableException)?.rejection) {
        OperationalContextRejection.NO_OUTLET -> HeldOrderUiError.NO_OUTLET
        OperationalContextRejection.NO_OPEN_SHIFT -> HeldOrderUiError.NO_OPEN_SHIFT
        OperationalContextRejection.SHIFT_OUTLET_MISMATCH -> HeldOrderUiError.SHIFT_OUTLET_MISMATCH
        null -> HeldOrderUiError.UNKNOWN
    }

    private fun mapHeldOrderError(error: Throwable): HeldOrderUiError = when {
        error is HeldOrderError && error.errorCode == "HELD_ORDER_VERSION_CONFLICT" -> HeldOrderUiError.VERSION_CONFLICT
        error is HeldOrderError && error.errorCode == "HELD_ORDER_NOT_OPEN" -> HeldOrderUiError.NOT_OPEN
        error is HeldOrderError && error.errorCode == "INVALID_CASHIER_SESSION" -> HeldOrderUiError.RESOURCE_CONFLICT
        error is HeldOrderError && error.errorCode == "HELD_ORDER_NOT_FOUND" -> HeldOrderUiError.NOT_FOUND
        error is java.io.IOException -> HeldOrderUiError.NETWORK
        else -> HeldOrderUiError.UNKNOWN
    }

    private fun handleUpdate(result: CartUpdateResult) {
        _state.update { current ->
            current.copy(
                errorMessage = when (result) {
                    CartUpdateResult.UPDATED -> null
                    CartUpdateResult.STOCK_LIMIT_REACHED ->
                        "Jumlah di cart sudah mencapai stok yang tersedia."
                },
            )
        }
    }

    internal companion object {
        internal const val OPEN_SHIFT_STATUS = "OPEN"
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

data class OperationalContext(
    val outletId: String,
    val cashierSessionId: String,
)

enum class OperationalContextRejection {
    NO_OUTLET,
    NO_OPEN_SHIFT,
    SHIFT_OUTLET_MISMATCH,
}

class OperationalContextUnavailableException(
    val rejection: OperationalContextRejection,
) : Exception(rejection.name)
