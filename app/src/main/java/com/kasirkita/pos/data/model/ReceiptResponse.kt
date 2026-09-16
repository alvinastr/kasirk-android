package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName
import com.kasirkita.pos.domain.model.Payment
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.ReceiptCashier
import com.kasirkita.pos.domain.model.ReceiptCustomer
import com.kasirkita.pos.domain.model.ReceiptItem
import com.kasirkita.pos.domain.model.ReceiptOutlet
import com.kasirkita.pos.domain.model.ReceiptTenant

data class ReceiptResponse(
    @SerializedName("transaction_id")
    val transactionId: String,
    @SerializedName("client_transaction_id")
    val clientTransactionId: String,
    val status: String,
    @SerializedName("created_at")
    val createdAt: String,
    val store: ReceiptStoreResponse,
    val outlet: ReceiptOutletResponse,
    val cashier: ReceiptCashierResponse,
    val customer: ReceiptCustomerResponse?,
    val items: List<ReceiptItemResponse>,
    val payment: ReceiptPaymentResponse?,
    val totals: ReceiptTotalsResponse,
    val change: Long?,
)

data class ReceiptStoreResponse(
    val id: String,
    val name: String,
    val address: String?,
)

data class ReceiptOutletResponse(
    val id: String,
    val name: String,
    val address: String?,
)

data class ReceiptCashierResponse(
    val id: String,
    val name: String,
)

data class ReceiptCustomerResponse(
    val id: String,
    val name: String,
    val phone: String?,
    val email: String?,
)

data class ReceiptItemResponse(
    @SerializedName("item_id")
    val itemId: String,
    @SerializedName("product_id")
    val productId: String,
    @SerializedName("product_name")
    val productName: String,
    val sku: String,
    val quantity: Int,
    @SerializedName("unit_price")
    val unitPrice: Long,
    val subtotal: Long,
)

data class ReceiptPaymentResponse(
    val id: String,
    val method: String,
    val status: String,
    val amount: Long,
    val provider: String?,
    @SerializedName("provider_reference")
    val providerReference: String?,
    @SerializedName("paid_at")
    val paidAt: String?,
)

data class ReceiptTotalsResponse(
    val subtotal: Long,
    val discount: Long,
    val tax: Long,
    val total: Long,
)

fun ReceiptResponse.toDomain(): Receipt = Receipt(
    transactionId = transactionId,
    clientTransactionId = clientTransactionId,
    status = status,
    createdAt = createdAt,
    tenant = ReceiptTenant(
        id = store.id,
        name = store.name,
        address = store.address,
    ),
    outlet = ReceiptOutlet(
        id = outlet.id,
        name = outlet.name,
        address = outlet.address,
    ),
    cashier = ReceiptCashier(
        id = cashier.id,
        name = cashier.name,
    ),
    customer = customer?.let {
        ReceiptCustomer(
            id = it.id,
            name = it.name,
            phone = it.phone,
            email = it.email,
        )
    },
    items = items.map { item ->
        ReceiptItem(
            id = item.itemId,
            productId = item.productId,
            productName = item.productName,
            sku = item.sku,
            quantity = item.quantity,
            unitPrice = item.unitPrice,
            subtotal = item.subtotal,
        )
    },
    payment = payment?.let {
        Payment(
            id = it.id,
            method = it.method,
            status = it.status,
            amount = it.amount,
            paidAt = it.paidAt,
        )
    },
    subtotal = totals.subtotal,
    discount = totals.discount,
    tax = totals.tax,
    total = totals.total,
    change = change,
)
