package com.kasirkita.pos.data.model

data class SyncTransactionsRequest(
    val transactions: List<CreateTransactionRequest>,
)
