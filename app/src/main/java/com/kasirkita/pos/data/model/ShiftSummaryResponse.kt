package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName
import com.kasirkita.pos.domain.model.ShiftProductSummary
import com.kasirkita.pos.domain.model.ShiftSummary
import com.kasirkita.pos.domain.model.ShiftSummaryParty
import com.kasirkita.pos.domain.model.ShiftSummaryTotals

data class ShiftSummaryResponse(
    @SerializedName("shift_id")
    val shiftId: String,
    val status: String,
    val outlet: ShiftSummaryPartyResponse,
    val cashier: ShiftSummaryPartyResponse,
    @SerializedName("opened_at")
    val openedAt: String,
    @SerializedName("closed_at")
    val closedAt: String?,
    @SerializedName("generated_at")
    val generatedAt: String,
    @SerializedName("transaction_count")
    val transactionCount: Int,
    val totals: ShiftSummaryTotalsResponse,
    val products: List<ShiftSummaryProductResponse>,
)

data class ShiftSummaryPartyResponse(
    val id: String,
    val name: String,
)

data class ShiftSummaryTotalsResponse(
    val sales: Long,
    val cash: Long,
    val qris: Long,
    @SerializedName("edc")
    val edc: Long = 0L,
)

data class ShiftSummaryProductResponse(
    @SerializedName("product_id")
    val productId: String,
    @SerializedName("product_name")
    val productName: String,
    val quantity: Int,
)

fun ShiftSummaryResponse.toDomain(): ShiftSummary = ShiftSummary(
    shiftId = shiftId,
    status = status,
    outlet = outlet.toDomain(),
    cashier = cashier.toDomain(),
    openedAt = openedAt,
    closedAt = closedAt,
    generatedAt = generatedAt,
    transactionCount = transactionCount,
    totals = ShiftSummaryTotals(
        sales = totals.sales,
        cash = totals.cash,
        qris = totals.qris,
        edc = totals.edc,
    ),
    products = products.map { product ->
        ShiftProductSummary(
            productId = product.productId,
            productName = product.productName,
            quantity = product.quantity,
        )
    },
)

private fun ShiftSummaryPartyResponse.toDomain(): ShiftSummaryParty = ShiftSummaryParty(
    id = id,
    name = name,
)
