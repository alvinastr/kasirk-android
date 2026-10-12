package com.kasirkita.pos.presentation.offline

import com.kasirkita.pos.domain.model.OfflineTransaction
import com.kasirkita.pos.domain.model.OfflineTransactionFailureType

data class OfflineRecoveryState(
    val isLoading: Boolean = true,
    val transactions: List<OfflineTransaction> = emptyList(),
    val processingClientTransactionId: String? = null,
    val noticeMessage: String? = null,
    val errorMessage: String? = null,
)

enum class OfflineRecoveryAction {
    RETRY,
    DELETE,
    ACKNOWLEDGE,
}

internal fun recoveryActionsFor(
    failureType: OfflineTransactionFailureType?,
): Set<OfflineRecoveryAction> = emptySet()
