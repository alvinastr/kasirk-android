package com.kasirkita.pos.presentation.cart

import com.kasirkita.pos.domain.model.Cart
import com.kasirkita.pos.domain.model.HeldOrder
import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.domain.model.Shift

enum class HeldOrderOperation { LIST, SAVE, RESTORE, UPDATE, CANCEL }

data class HeldOrderContextKey(
    val tenantId: String,
    val userId: String,
    val outletId: String,
    val shiftId: String,
    val cashierSessionId: String,
)

sealed interface HeldOrderTotalState {
    data object NotLoaded : HeldOrderTotalState
    data class Loading(val previousFreshValue: Int? = null) : HeldOrderTotalState
    data class Available(val total: Int) : HeldOrderTotalState
    data class Stale(val previousValue: Int, val reason: String) : HeldOrderTotalState
    data class Error(val reason: String) : HeldOrderTotalState
}

internal fun heldOrderBadgeCount(state: HeldOrderTotalState): Int? =
    (state as? HeldOrderTotalState.Available)?.total

enum class HeldOrderUiError {
    NO_OUTLET, NO_OPEN_SHIFT, SHIFT_OUTLET_MISMATCH, EMPTY_CART, CART_CHANGED,
    CATALOG_CONFLICT, STOCK_CONFLICT, VERSION_CONFLICT, NOT_OPEN,
    NOT_FOUND, RESOURCE_CONFLICT, AUTHENTICATION_REQUIRED, ACCESS_DENIED,
    NETWORK, UNKNOWN
}

data class CartState(
    val cart: Cart = Cart(),
    val errorMessage: String? = null,
    val selectedOutlet: Outlet? = null,
    val currentShift: Shift? = null,
    val isEditingHeldOrder: Boolean = false,
    val heldOrderId: String? = null,
    val heldOrderExpectedVersion: Int? = null,
    val heldOrderLabel: String? = null,
    val heldOrders: List<HeldOrder> = emptyList(),
    val heldOrdersLoaded: Boolean = false,
    val heldOrdersOutletId: String? = null,
    val heldOrdersShiftId: String? = null,
    val heldOrderContext: HeldOrderContextKey? = null,
    val heldOrderTotalState: HeldOrderTotalState = HeldOrderTotalState.NotLoaded,
    val heldOrderOperation: HeldOrderOperation? = null,
    val heldOrderError: HeldOrderUiError? = null,
)
