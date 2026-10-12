package com.kasirkita.pos.core.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "online_transaction_journal",
    primaryKeys = ["tenantId", "userId", "outletId", "clientTransactionId"],
    indices = [
        Index(value = ["tenantId", "userId", "state"]),
    ],
)
data class OnlineTransactionJournalEntity(
    val tenantId: String,
    val userId: String,
    val outletId: String,
    val clientTransactionId: String,
    val requestType: String,
    val requestJson: String,
    val paymentMethod: String,
    val state: String,
    val serverTransactionId: String?,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
