package com.kasirkita.pos.data.repository

import com.kasirkita.pos.data.api.ReportApi
import com.kasirkita.pos.data.model.toDomain
import com.kasirkita.pos.domain.model.DailySalesReport
import com.kasirkita.pos.domain.model.SalesSummaryReport
import com.kasirkita.pos.domain.model.TopProductReport
import com.kasirkita.pos.domain.repository.ReportRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportRepositoryImpl @Inject constructor(
    private val reportApi: ReportApi,
) : ReportRepository {

    override suspend fun getDailySales(
        date: String,
        outletId: String?,
    ): Result<DailySalesReport> = runCatching {
        reportApi.getDailySales(
            date = date,
            outletId = outletId,
        ).toDomain()
    }

    override suspend fun getSalesSummary(
        startDate: String,
        endDate: String,
        outletId: String?,
    ): Result<SalesSummaryReport> = runCatching {
        reportApi.getSalesSummary(
            startDate = startDate,
            endDate = endDate,
            outletId = outletId,
        ).toDomain()
    }

    override suspend fun getTopProducts(
        startDate: String,
        endDate: String,
        limit: Int,
        outletId: String?,
    ): Result<List<TopProductReport>> = runCatching {
        reportApi.getTopProducts(
            startDate = startDate,
            endDate = endDate,
            limit = limit,
            outletId = outletId,
        ).map { response -> response.toDomain() }
    }
}
