package com.kasirkita.pos.domain.repository

import com.kasirkita.pos.domain.model.Stock
import com.kasirkita.pos.domain.model.StockAdjustmentType

interface StockRepository {
    suspend fun getStocks(outletId: String): Result<List<Stock>>

    suspend fun createAdjustment(
        outletId: String,
        productId: String,
        adjustmentType: StockAdjustmentType,
        quantity: Int,
        reason: String?,
    ): Result<Stock>
}
