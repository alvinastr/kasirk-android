package com.kasirkita.pos.presentation.checkout

import com.kasirkita.pos.domain.model.Cart
import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.domain.model.Shift
import com.kasirkita.pos.domain.model.Transaction

data class CheckoutState(
    val cart: Cart = Cart(),
    val selectedOutlet: Outlet? = null,
    val currentShift: Shift? = null,
    val payment: CheckoutPaymentState = CheckoutPaymentState(),
    val isLoading: Boolean = false,
    val transaction: Transaction? = null,
    val offlineQueuedClientTransactionId: String? = null,
    val persistedTotal: Long = 0L,
    val errorMessage: String? = null,
)
