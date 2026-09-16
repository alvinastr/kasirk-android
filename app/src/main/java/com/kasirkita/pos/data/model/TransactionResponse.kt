package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName
import com.kasirkita.pos.domain.model.Payment
import com.kasirkita.pos.domain.model.Transaction
import com.kasirkita.pos.domain.model.TransactionItem

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
    val status: String,
    val subtotal: Long,
    val discount: Long,
    val tax: Long,
    val total: Long,
    val items: List<TransactionItemResponse>,
    val payments: List<PaymentResponse>,
    val change: Long?,
    @SerializedName("created_at")
    val createdAt: String,
)

data class TransactionItemResponse(
    val id: String,
    @SerializedName("product_id")
    val productId: String,
    val quantity: Int,
    @SerializedName("unit_price")
    val unitPrice: Long,
    val subtotal: Long,
)

data class PaymentResponse(
    val id: String,
    val method: String,
    val status: String,
    val amount: Long,
    @SerializedName("paid_at")
    val paidAt: String?,
)

fun TransactionResponse.toDomain(): Transaction = Transaction(
    id = transactionId,
    clientTransactionId = clientTransactionId,
    outletId = outletId,
    userId = userId,
    customerId = customerId,
    status = status,
    subtotal = subtotal,
    discount = discount,
    tax = tax,
    total = total,
    items = items.map { item ->
        TransactionItem(
            id = item.id,
            productId = item.productId,
            quantity = item.quantity,
            unitPrice = item.unitPrice,
            subtotal = item.subtotal,
        )
    },
    payments = payments.map { payment ->
        Payment(
            id = payment.id,
            method = payment.method,
            status = payment.status,
            amount = payment.amount,
            paidAt = payment.paidAt,
        )
    },
    change = change,
    createdAt = createdAt,
)
