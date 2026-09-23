package com.kasirkita.pos.presentation.stock

import com.kasirkita.pos.domain.model.Stock

data class StockAdjustmentState(
    val stocks: List<Stock> = emptyList(),
    val isLoadingStock: Boolean = false,
    val hasLoadedStocks: Boolean = false,
    val stockLoadError: String? = null,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val adjustmentSucceeded: Boolean = false,
) {
    fun currentStock(productId: String): Int =
        stocks.firstOrNull { stock -> stock.productId == productId }?.quantity ?: 0
}
