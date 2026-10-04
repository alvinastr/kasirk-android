package com.kasirkita.pos.data.model

import com.kasirkita.pos.domain.model.V1Payment
import com.kasirkita.pos.domain.model.V1TransactionItemRequest
import com.kasirkita.pos.domain.model.V1TransactionRequest

fun V1TransactionRequest.toNetwork(): V1CreateTransactionRequest = V1CreateTransactionRequest(
    clientTransactionId = clientTransactionId,
    outletId = outletId,
    cashierSessionId = cashierSessionId,
    customerId = customerId,
    items = items.map { item -> item.toNetwork() },
    payment = payment.toNetwork(),
    discount = discount,
)

fun V1TransactionItemRequest.toNetwork(): V1CreateTransactionItemRequest = V1CreateTransactionItemRequest(
    productId = productId,
    quantity = quantity,
    modifierOptionIds = modifierOptionIds,
    note = note,
)

fun V1Payment.toNetwork(): V1PaymentRequest = V1PaymentRequest(
    method = method,
    amountReceived = amountReceived,
)
