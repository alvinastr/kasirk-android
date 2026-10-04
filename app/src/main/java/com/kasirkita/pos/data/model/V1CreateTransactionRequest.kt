package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName

/**
 * V1 online transaction request model.
 *
 * This is intentionally separate from [CreateTransactionRequest] which is used by the
 * RC2 offline queue payload. M9 will migrate the persisted queue to V2; until then,
 * the offline queue continues using the legacy request shape.
 */
data class V1CreateTransactionRequest(
    @SerializedName("client_transaction_id")
    val clientTransactionId: String,
    @SerializedName("outlet_id")
    val outletId: String,
    @SerializedName("cashier_session_id")
    val cashierSessionId: String,
    @SerializedName("customer_id")
    val customerId: String?,
    val items: List<V1CreateTransactionItemRequest>,
    val payment: V1PaymentRequest,
    val discount: Long? = null,
)

data class V1CreateTransactionItemRequest(
    @SerializedName("product_id")
    val productId: String,
    val quantity: Int,
    @SerializedName("modifier_option_ids")
    val modifierOptionIds: List<String> = emptyList(),
    val note: String? = null,
)

data class V1PaymentRequest(
    val method: String,
    @SerializedName("amount_received")
    val amountReceived: Long? = null,
)
