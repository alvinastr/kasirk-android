package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName
import com.kasirkita.pos.domain.model.CartItem

data class CreateTransactionRequest(
    @SerializedName("client_transaction_id")
    val clientTransactionId: String,
    @SerializedName("outlet_id")
    val outletId: String,
    @SerializedName("customer_id")
    val customerId: String?,
    val items: List<CreateTransactionItemRequest>,
    val payment: PaymentRequest,
)

data class CreateTransactionItemRequest(
    @SerializedName("product_id")
    val productId: String,
    val quantity: Int,
)

fun createTransactionRequest(
    clientTransactionId: String,
    outletId: String,
    customerId: String?,
    items: List<CartItem>,
    paymentAmount: Long,
): CreateTransactionRequest = CreateTransactionRequest(
    clientTransactionId = clientTransactionId,
    outletId = outletId,
    customerId = customerId,
    items = items.map { item ->
        CreateTransactionItemRequest(
            productId = item.productId,
            quantity = item.quantity,
        )
    },
    payment = PaymentRequest(
        method = "CASH",
        amount = paymentAmount,
    ),
)
