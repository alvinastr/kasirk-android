package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName
import com.kasirkita.pos.domain.model.TopProductReport

data class TopProductResponse(
    @SerializedName("product_id")
    val productId: String,
    @SerializedName("product_name")
    val productName: String,
    val sku: String,
    @SerializedName("quantity_sold")
    val quantitySold: Int,
    @SerializedName("gross_sales")
    val grossSales: Long,
    val cogs: Long,
    @SerializedName("gross_profit")
    val grossProfit: Long,
)

fun TopProductResponse.toDomain(): TopProductReport = TopProductReport(
    productId = productId,
    productName = productName,
    sku = sku,
    quantitySold = quantitySold,
    grossSales = grossSales,
    cogs = cogs,
    grossProfit = grossProfit,
)
