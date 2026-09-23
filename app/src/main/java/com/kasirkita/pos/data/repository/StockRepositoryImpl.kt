package com.kasirkita.pos.data.repository

import com.kasirkita.pos.data.api.StockApi
import com.kasirkita.pos.data.model.CreateStockAdjustmentRequest
import com.kasirkita.pos.data.model.toDomain
import com.kasirkita.pos.domain.model.Stock
import com.kasirkita.pos.domain.model.StockAdjustmentType
import com.kasirkita.pos.domain.repository.StockRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockRepositoryImpl @Inject constructor(
    private val stockApi: StockApi,
) : StockRepository {

    override suspend fun getStocks(outletId: String): Result<List<Stock>> = runCatching {
        stockApi.getStocks(outletId).map { response -> response.toDomain() }
    }

    override suspend fun createAdjustment(
        outletId: String,
        productId: String,
        adjustmentType: StockAdjustmentType,
        quantity: Int,
        reason: String?,
    ): Result<Stock> = runCatching {
        stockApi.createAdjustment(
            CreateStockAdjustmentRequest(
                outletId = outletId,
                productId = productId,
                adjustmentType = adjustmentType,
                quantity = quantity,
                reason = reason,
            ),
        ).toDomain()
    }
}
