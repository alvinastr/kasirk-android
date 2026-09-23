package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName
import com.kasirkita.pos.domain.model.Stock

data class StockResponse(
    val id: String,
    @SerializedName("outlet_id")
    val outletId: String,
    @SerializedName("product_id")
    val productId: String,
    val stock: Int,
    @SerializedName("updated_at")
    val updatedAt: String,
    val products: StockProductResponse? = null,
)

data class StockProductResponse(
    val id: String,
    val name: String,
    val sku: String,
    @SerializedName("track_stock")
    val trackStock: Boolean,
)

fun StockResponse.toDomain(): Stock = Stock(
    id = id,
    outletId = outletId,
    productId = productId,
    quantity = stock,
    updatedAt = updatedAt,
    productName = products?.name,
    productSku = products?.sku,
)
