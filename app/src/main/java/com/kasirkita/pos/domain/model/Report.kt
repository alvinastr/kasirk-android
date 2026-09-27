package com.kasirkita.pos.domain.model

data class DailySalesReport(
    val date: String,
    val outletId: String?,
    val transactionCount: Int,
    val grossSales: Long,
    val discount: Long,
    val netSales: Long,
    val taxCollected: Long,
    val totalSales: Long,
    val cogs: Long,
    val grossProfit: Long,
)

data class SalesSummaryReport(
    val transactionCount: Int,
    val grossSales: Long,
    val discount: Long,
    val netSales: Long,
    val taxCollected: Long,
    val totalSales: Long,
    val cogs: Long,
    val grossProfit: Long,
    val averageTransaction: Long,
)

data class TopProductReport(
    val productId: String,
    val productName: String,
    val sku: String,
    val quantitySold: Int,
    val grossSales: Long,
    val cogs: Long,
    val grossProfit: Long,
)
