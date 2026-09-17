package com.kasirkita.pos.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "offline_transactions",
    indices = [
        Index(value = ["clientTransactionId"], unique = true),
    ],
)
data class OfflineTransactionEntity(
    @PrimaryKey
    val id: String,
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
