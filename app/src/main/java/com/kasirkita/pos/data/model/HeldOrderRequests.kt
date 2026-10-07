package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName

data class HeldOrderItemRequest(
    @SerializedName("product_id")
    val productId: String,
    val quantity: Int,
    @SerializedName("modifier_option_ids")
    val modifierOptionIds: List<String> = emptyList(),
    val note: String? = null,
)

data class CreateHeldOrderRequest(
    @SerializedName("outlet_id")
    val outletId: String,
    @SerializedName("cashier_session_id")
    val cashierSessionId: String,
    val label: String? = null,
    val items: List<HeldOrderItemRequest>,
)

data class UpdateHeldOrderRequest(
    @SerializedName("expected_version")
    val expectedVersion: Int,
    val label: String? = null,
    val items: List<HeldOrderItemRequest>? = null,
)

data class CancelHeldOrderRequest(
    @SerializedName("expected_version")
    val expectedVersion: Int,
)

data class CheckoutHeldOrderRequest(
    @SerializedName("expected_version")
    val expectedVersion: Int,
    @SerializedName("client_transaction_id")
    val clientTransactionId: String,
    val payment: V1PaymentRequest,
)