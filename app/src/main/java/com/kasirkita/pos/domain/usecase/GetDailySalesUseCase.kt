package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.DailySalesReport
import com.kasirkita.pos.domain.repository.ReportRepository
import javax.inject.Inject

class GetDailySalesUseCase @Inject constructor(
    private val repository: ReportRepository,
) {
    suspend operator fun invoke(
        date: String,
        outletId: String? = null,
    ): Result<DailySalesReport> = repository.getDailySales(
        date = date,
        outletId = outletId,
    )
}
