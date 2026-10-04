package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName
import com.kasirkita.pos.domain.model.Transaction

data class TransactionsResponse(
    val data: List<TransactionResponse>,
    val meta: TransactionsMetaResponse,
)

data class TransactionsMetaResponse(
    val page: Int,
    val limit: Int,
    val total: Int,
    @SerializedName("total_pages")
    val totalPages: Int,
)

data class TransactionResponse(
    @SerializedName("transaction_id")
    val transactionId: String,
    @SerializedName("client_transaction_id")
    val clientTransactionId: String,
    @SerializedName("outlet_id")
    val outletId: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("customer_id")
    val customerId: String?,
    @SerializedName("cashier_session_id")
    val cashierSessionId: String? = null,
    @SerializedName("shift_id")
    val shiftId: String? = null,
    val status: String,
    val subtotal: Long,
    val discount: Long,
    val tax: Long,
    val total: Long,
    @SerializedName("created_at")
    val createdAt: String,
)

fun TransactionResponse.toDomain(): Transaction = Transaction(
    id = transactionId,
    clientTransactionId = clientTransactionId,
    outletId = outletId,
    userId = userId,
    customerId = customerId,
    cashierSessionId = cashierSessionId,
    shiftId = shiftId,
    status = status,
    subtotal = subtotal,
    discount = discount,
    tax = tax,
    total = total,
    items = emptyList(),
    payments = emptyList(),
    change = null,
    createdAt = createdAt,
)
