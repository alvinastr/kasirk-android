package com.kasirkita.pos.domain.model

data class ShiftSummary(
    val shiftId: String,
    val status: String,
    val outlet: ShiftSummaryParty,
    val cashier: ShiftSummaryParty,
    val openedAt: String,
    val closedAt: String?,
    val generatedAt: String,
    val transactionCount: Int,
    val totals: ShiftSummaryTotals,
    val products: List<ShiftProductSummary>,
)

data class ShiftSummaryParty(
    val id: String,
    val name: String,
)

data class ShiftSummaryTotals(
    val sales: Long,
    val cash: Long,
    val qris: Long,
    val edc: Long = 0L,
)

data class ShiftProductSummary(
    val productId: String,
    val productName: String,
    val quantity: Int,
)
