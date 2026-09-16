package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName

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
