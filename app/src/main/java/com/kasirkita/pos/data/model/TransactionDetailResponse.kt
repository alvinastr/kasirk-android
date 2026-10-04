package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName
import com.kasirkita.pos.domain.model.ModifierSnapshot
import com.kasirkita.pos.domain.model.Payment
import com.kasirkita.pos.domain.model.Transaction
import com.kasirkita.pos.domain.model.TransactionItem

data class TransactionDetailResponse(
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
    @SerializedName("product_name_snapshot")
    val productNameSnapshot: String? = null,
    @SerializedName("sku_snapshot")
    val skuSnapshot: String? = null,
    @SerializedName("base_price_snapshot")
    val basePriceSnapshot: Long? = null,
    @SerializedName("effective_price_snapshot")
    val effectivePriceSnapshot: Long? = null,
    @SerializedName("note_snapshot")
    val noteSnapshot: String? = null,
    @SerializedName("product_name")
    val productName: String? = null,
    val sku: String? = null,
    @SerializedName("base_price")
    val basePrice: Long? = null,
    @SerializedName("effective_price")
    val effectivePrice: Long? = null,
    val note: String? = null,
    val modifiers: List<TransactionItemModifierResponse>? = null,
)

data class TransactionItemModifierResponse(
    val id: String,
    @SerializedName("modifier_group_id")
    val modifierGroupId: String?,
    @SerializedName("modifier_option_id")
    val modifierOptionId: String?,
    @SerializedName("group_name_snapshot")
    val groupNameSnapshot: String,
    @SerializedName("option_name_snapshot")
    val optionNameSnapshot: String,
    @SerializedName("price_delta_snapshot")
    val priceDeltaSnapshot: Long,
)

data class PaymentResponse(
    val id: String,
    val method: String,
    val status: String,
    val amount: Long,
    @SerializedName("amount_received")
    val amountReceived: Long? = null,
    @SerializedName("change_amount")
    val changeAmount: Long? = null,
    @SerializedName("paid_at")
    val paidAt: String?,
)

fun TransactionDetailResponse.toDomain(): Transaction = Transaction(
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
    items = items.map { item ->
        TransactionItem(
            id = item.id,
            productId = item.productId,
            quantity = item.quantity,
            unitPrice = item.unitPrice,
            subtotal = item.subtotal,
            modifierSnapshots = item.modifiers.orEmpty().map { modifier ->
                ModifierSnapshot(
                    id = modifier.id,
                    modifierGroupId = modifier.modifierGroupId,
                    modifierOptionId = modifier.modifierOptionId,
                    groupName = modifier.groupNameSnapshot,
                    optionName = modifier.optionNameSnapshot,
                    priceDelta = modifier.priceDeltaSnapshot,
                )
            },
            productName = item.productNameSnapshot ?: item.productName,
            sku = item.skuSnapshot ?: item.sku,
            productNameSnapshot = item.productNameSnapshot,
            skuSnapshot = item.skuSnapshot,
            basePriceSnapshot = item.basePriceSnapshot,
            effectivePriceSnapshot = item.effectivePriceSnapshot,
            note = item.noteSnapshot ?: item.note,
        )
    },
    payments = payments.map { payment ->
        Payment(
            id = payment.id,
            method = payment.method,
            status = payment.status,
            amount = payment.amount,
            paidAt = payment.paidAt,
            amountReceived = payment.amountReceived,
            changeAmount = payment.changeAmount,
        )
    },
    change = change,
    createdAt = createdAt,
)
