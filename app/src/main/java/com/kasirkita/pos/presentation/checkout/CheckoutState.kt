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
    val printerWarning: String? = null,
    /**
     * M16G-S1: the authoritative `replayed` flag of a Held Order checkout result.
     *
     * Recorded so a later slice can make physical side effects replay-aware, but
     * deliberately inert in S1: it must never gate printing or the cash drawer.
     * `null` means the current [transaction] did not come from a Held Order
     * checkout, so the normal (non-held-order) checkout path stays unchanged.
     */
    val heldOrderReplayed: Boolean? = null,

    // M16G-S2: Held Order reconciliation / unknown-outcome recovery state
    /**
     * True when a Held Order checkout has been attempted but the client cannot
     * determine whether the server committed it (IOException / transport failure).
     * In this state the pending request metadata is frozen and must be replayed
     * exactly via [retryHeldOrderCheckout()].
     */
    val reconciliationNeeded: Boolean = false,

    /**
     * Human-readable message shown when [reconciliationNeeded] is true.
     * Explains that the payment status is unknown and an explicit retry is required.
     */
    val reconciliationMessage: String? = null,
)
