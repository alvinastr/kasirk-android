package com.kasirkita.pos.core.policy

/** Pilot policy for held-order checkout. Existing server records remain intact. */
fun interface HeldOrderPilotPolicy {
    fun allowsCheckout(): Boolean
}

const val HELD_ORDER_CHECKOUT_ENABLED = true
