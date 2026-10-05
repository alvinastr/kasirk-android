package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.OfflineFinancialSnapshot
import com.kasirkita.pos.domain.model.OfflineTransaction
import com.kasirkita.pos.domain.model.V1TransactionRequest
import com.kasirkita.pos.domain.repository.OfflineSyncRepository
import javax.inject.Inject

class QueueOfflineTransactionUseCase @Inject constructor(
    private val repository: OfflineSyncRepository,
) {
    suspend operator fun invoke(
        request: V1TransactionRequest,
        financialSnapshot: OfflineFinancialSnapshot,
    ): Result<OfflineTransaction> = repository.queueV1Transaction(
        request = request,
        financialSnapshot = financialSnapshot,
    )
}
