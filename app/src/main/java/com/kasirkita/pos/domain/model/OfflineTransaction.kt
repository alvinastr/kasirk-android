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
)

object OfflineTransactionStatus {
    const val PENDING = "PENDING"
    const val SYNCED = "SYNCED"
    const val FAILED = "FAILED"
}
