package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName
import com.kasirkita.pos.domain.model.DailySalesReport

data class DailySalesResponse(
    val date: String,
    @SerializedName("outlet_id")
    val outletId: String?,
    @SerializedName("transaction_count")
    val transactionCount: Int,
    @SerializedName("gross_sales")
    val grossSales: Long,
    val discount: Long,
    @SerializedName("net_sales")
    val netSales: Long,
    @SerializedName("tax_collected")
    val taxCollected: Long,
    @SerializedName("total_sales")
    val totalSales: Long,
    val cogs: Long,
    @SerializedName("gross_profit")
    val grossProfit: Long,
)

fun DailySalesResponse.toDomain(): DailySalesReport = DailySalesReport(
    date = date,
    outletId = outletId,
    transactionCount = transactionCount,
    grossSales = grossSales,
    discount = discount,
    netSales = netSales,
    taxCollected = taxCollected,
    totalSales = totalSales,
    cogs = cogs,
    grossProfit = grossProfit,
)
