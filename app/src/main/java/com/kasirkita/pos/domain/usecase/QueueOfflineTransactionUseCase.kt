package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.OfflineFinancialSnapshot
import com.kasirkita.pos.domain.model.OfflineTransaction
import com.kasirkita.pos.domain.repository.OfflineSyncRepository
import javax.inject.Inject

class QueueOfflineTransactionUseCase @Inject constructor(
    private val repository: OfflineSyncRepository,
) {
    suspend operator fun invoke(
        clientTransactionId: String,
        outletId: String,
        customerId: String?,
        items: List<CartItem>,
        paymentAmount: Long,
        financialSnapshot: OfflineFinancialSnapshot,
    ): Result<OfflineTransaction> = repository.queueTransaction(
        clientTransactionId = clientTransactionId,
        outletId = outletId,
        customerId = customerId,
        items = items,
        paymentAmount = paymentAmount,
        financialSnapshot = financialSnapshot,
    )
}
