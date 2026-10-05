package com.kasirkita.pos.data.model

import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.V1Payment
import com.kasirkita.pos.domain.model.V1TransactionItemRequest
import com.kasirkita.pos.domain.model.V1TransactionRequest

/**
 * Build V1 transaction request from cart items.
 *
 * Preserves modifier option IDs and per-item notes from CartItem configurations.
 * Requires caller to supply outlet, session, payment details.
 */
fun buildV1TransactionRequest(
    clientTransactionId: String,
    outletId: String,
    cashierSessionId: String,
    items: List<CartItem>,
    payment: V1Payment,
    customerId: String? = null,
    discount: Long? = null,
): V1TransactionRequest = V1TransactionRequest(
    clientTransactionId = clientTransactionId,
    outletId = outletId,
    cashierSessionId = cashierSessionId,
    customerId = customerId,
    items = items.map { item ->
        V1TransactionItemRequest(
            productId = item.productId,
            quantity = item.quantity,
            modifierOptionIds = item.modifierOptionIds,
            note = item.note,
        )
    },
    payment = payment,
    discount = discount,
)
