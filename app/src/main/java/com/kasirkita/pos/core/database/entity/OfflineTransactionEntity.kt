package com.kasirkita.pos.core.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "offline_transactions",
    primaryKeys = ["tenantId", "userId", "clientTransactionId"],
    indices = [
        Index(value = ["tenantId", "userId", "status", "createdAt"]),
    ],
)
data class OfflineTransactionEntity(
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
