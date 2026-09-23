package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName
import com.kasirkita.pos.domain.model.StockAdjustmentType

data class CreateStockAdjustmentRequest(
    @SerializedName("outlet_id")
    val outletId: String,
    @SerializedName("product_id")
    val productId: String,
    @SerializedName("adjustment_type")
    val adjustmentType: StockAdjustmentType,
    val quantity: Int,
    val reason: String?,
)
