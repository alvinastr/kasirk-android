package com.kasirkita.pos.domain.model

data class OfflineQueueSummary(
    val pendingCount: Int,
    val failedCount: Int,
)
