package com.kasirkita.pos.domain.repository

import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.Transaction

interface TransactionRepository {
    suspend fun getTransactions(): Result<List<Transaction>>

    suspend fun getTransactionDetail(transactionId: String): Result<Transaction>

    suspend fun createTransaction(
        clientTransactionId: String,
        outletId: String,
        customerId: String?,
        items: List<CartItem>,
        paymentAmount: Long,
    ): Result<Transaction>
}
