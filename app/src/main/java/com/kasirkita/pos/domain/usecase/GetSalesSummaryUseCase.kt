package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.SalesSummaryReport
import com.kasirkita.pos.domain.repository.ReportRepository
import javax.inject.Inject

class GetSalesSummaryUseCase @Inject constructor(
    private val repository: ReportRepository,
) {
    suspend operator fun invoke(
        startDate: String,
        endDate: String,
        outletId: String? = null,
    ): Result<SalesSummaryReport> = repository.getSalesSummary(
        startDate = startDate,
        endDate = endDate,
        outletId = outletId,
    )
}
