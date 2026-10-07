package com.kasirkita.pos.presentation.cart

import com.kasirkita.pos.domain.model.Cart
import com.kasirkita.pos.domain.model.HeldOrder
import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.domain.model.Shift

enum class HeldOrderOperation { LIST, SAVE, RESTORE, UPDATE, CANCEL }

enum class HeldOrderUiError {
    NO_OUTLET, NO_OPEN_SHIFT, SHIFT_OUTLET_MISMATCH, EMPTY_CART, CART_CHANGED,
    CATALOG_CONFLICT, STOCK_CONFLICT, VERSION_CONFLICT, NOT_OPEN,
    NOT_FOUND, RESOURCE_CONFLICT, NETWORK, UNKNOWN
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
    val heldOrderOperation: HeldOrderOperation? = null,
    val heldOrderError: HeldOrderUiError? = null,
)
