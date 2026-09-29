package com.kasirkita.pos.domain.model

sealed interface SyncOutcome {
    data class Completed(
        val result: SyncResult,
    ) : SyncOutcome

    data class RetryableFailure(
        val reason: SyncRetryableFailureReason,
        val result: SyncResult,
        val message: String,
    ) : SyncOutcome

    data object AuthenticationUnavailable : SyncOutcome

    data class ActionRequired(
        val result: SyncResult,
        val failedTransactions: List<OfflineTransaction>,
    ) : SyncOutcome
}

enum class SyncRetryableFailureReason {
    TRANSPORT,
    SERVER,
}
