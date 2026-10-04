package com.kasirkita.pos.domain.model

/**
 * Domain representation for a V1 online transaction request.
 *
 * Kept separate from the legacy offline queue domain model to avoid
 * accidentally coupling persisted queue semantics to V1 network changes.
 */
data class V1TransactionRequest(
    val clientTransactionId: String,
    val outletId: String,
    val cashierSessionId: String,
    val customerId: String?,
    val items: List<V1TransactionItemRequest>,
    val payment: V1Payment,
    val discount: Long? = null,
)

data class V1TransactionItemRequest(
    val productId: String,
    val quantity: Int,
    val modifierOptionIds: List<String>,
    val note: String?,
)

data class V1Payment(
    val method: String,
    val amountReceived: Long?,
)
