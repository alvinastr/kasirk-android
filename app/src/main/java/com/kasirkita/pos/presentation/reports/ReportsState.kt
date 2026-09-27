package com.kasirkita.pos.presentation.reports

import com.kasirkita.pos.domain.model.DailySalesReport
import com.kasirkita.pos.domain.model.TopProductReport

sealed interface ReportsState {
    data object Loading : ReportsState

    data class Success(
        val dailySales: DailySalesReport,
        val topProducts: List<TopProductReport>,
    ) : ReportsState

    data class Empty(
        val dailySales: DailySalesReport,
    ) : ReportsState

    data class Error(val message: String) : ReportsState
}
