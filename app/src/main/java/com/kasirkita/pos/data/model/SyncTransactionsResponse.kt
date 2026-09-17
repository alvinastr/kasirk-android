package com.kasirkita.pos.data.model

data class SyncTransactionsResponse(
    val total: Int,
    val synced: Int,
    val failed: Int,
    val results: List<SyncTransactionResult>,
)
