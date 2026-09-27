package com.kasirkita.pos.domain.repository

import com.kasirkita.pos.domain.model.DailySalesReport
import com.kasirkita.pos.domain.model.SalesSummaryReport
import com.kasirkita.pos.domain.model.TopProductReport

interface ReportRepository {
    suspend fun getDailySales(
        date: String,
        outletId: String? = null,
    ): Result<DailySalesReport>

    suspend fun getSalesSummary(
        startDate: String,
        endDate: String,
        outletId: String? = null,
    ): Result<SalesSummaryReport>

    suspend fun getTopProducts(
        startDate: String,
        endDate: String,
        limit: Int,
        outletId: String? = null,
    ): Result<List<TopProductReport>>
}
