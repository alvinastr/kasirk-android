package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.SyncOutcome
import com.kasirkita.pos.domain.repository.OfflineSyncRepository
import javax.inject.Inject

class SyncPendingTransactionsUseCase @Inject constructor(
    private val repository: OfflineSyncRepository,
) {
    suspend operator fun invoke(): Result<SyncOutcome> =
        repository.syncPendingTransactions()
}
