package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.Stock
import com.kasirkita.pos.domain.model.StockAdjustmentType
import com.kasirkita.pos.domain.repository.StockRepository
import javax.inject.Inject

class CreateStockAdjustmentUseCase @Inject constructor(
    private val stockRepository: StockRepository,
) {
    suspend operator fun invoke(
        outletId: String,
        productId: String,
        adjustmentType: StockAdjustmentType,
        quantity: Int,
        reason: String?,
    ): Result<Stock> = stockRepository.createAdjustment(
        outletId = outletId,
        productId = productId,
        adjustmentType = adjustmentType,
        quantity = quantity,
        reason = reason,
    )
}
