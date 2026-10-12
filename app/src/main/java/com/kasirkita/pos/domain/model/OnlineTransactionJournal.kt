package com.kasirkita.pos.domain.model

data class OnlineTransactionJournal(
    val tenantId: String,
    val userId: String,
    val outletId: String,
    val clientTransactionId: String,
    val requestType: String,
    val requestJson: String,
    val paymentMethod: String,
    val state: OnlineTransactionJournalState,
    val serverTransactionId: String?,
    val lastError: String?,
)

enum class OnlineTransactionJournalState {
    PREPARED,
    SUBMITTING,
    UNCERTAIN,
    REQUIRES_RECONCILIATION,
    CONFIRMED,
    REJECTED,
}

data class OnlineTransactionJournalDraft(
    val tenantId: String,
    val userId: String,
    val outletId: String,
    val clientTransactionId: String,
    val requestType: String,
    val requestJson: String,
    val paymentMethod: String,
)
