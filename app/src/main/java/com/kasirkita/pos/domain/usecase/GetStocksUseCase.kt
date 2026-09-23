package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.Stock
import com.kasirkita.pos.domain.repository.StockRepository
import javax.inject.Inject

class GetStocksUseCase @Inject constructor(
    private val stockRepository: StockRepository,
) {
    suspend operator fun invoke(outletId: String): Result<List<Stock>> =
        stockRepository.getStocks(outletId)
}
