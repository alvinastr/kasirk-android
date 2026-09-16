package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.domain.repository.OutletRepository
import javax.inject.Inject

class GetOutletsUseCase @Inject constructor(
    private val outletRepository: OutletRepository,
) {
    suspend operator fun invoke(): Result<List<Outlet>> = outletRepository.getOutlets()
}
