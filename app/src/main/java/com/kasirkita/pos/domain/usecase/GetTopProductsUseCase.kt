package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.TopProductReport
import com.kasirkita.pos.domain.repository.ReportRepository
import javax.inject.Inject

class GetTopProductsUseCase @Inject constructor(
    private val repository: ReportRepository,
) {
    suspend operator fun invoke(
        startDate: String,
        endDate: String,
        limit: Int,
        outletId: String? = null,
    ): Result<List<TopProductReport>> = repository.getTopProducts(
        startDate = startDate,
        endDate = endDate,
        limit = limit,
        outletId = outletId,
    )
}
