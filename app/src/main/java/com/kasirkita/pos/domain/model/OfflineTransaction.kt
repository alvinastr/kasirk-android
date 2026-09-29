package com.kasirkita.pos.domain.model

data class OfflineTransaction(
    val id: String,
    val tenantId: String,
    val userId: String,
    val clientTransactionId: String,
    val outletId: String,
    val payloadJson: String,
    val status: String,
    val serverTransactionId: String?,
    val lastError: String?,
    val retryCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val failureType: OfflineTransactionFailureType? = null,
    val summary: OfflineTransactionSummary? = null,
)

enum class OfflineTransactionFailureType {
    RETRYABLE,
    REJECTED,
    RECONCILIATION_REQUIRED,
}

data class OfflineTransactionSummary(
    val itemCount: Int,
    val subtotal: Long,
    val discount: Long,
    val tax: Long,
    val total: Long,
    val paymentMethod: String,
    val paymentAmount: Long,
)

object OfflineTransactionStatus {
    const val PENDING = "PENDING"
    const val RETRYABLE = "RETRYABLE"
    const val SYNCED = "SYNCED"
    const val FAILED = "FAILED"
    const val RECONCILIATION_REQUIRED = "RECONCILIATION_REQUIRED"
}
