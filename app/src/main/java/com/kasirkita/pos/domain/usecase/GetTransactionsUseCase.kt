package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.Transaction
import com.kasirkita.pos.domain.repository.TransactionRepository
import javax.inject.Inject

class GetTransactionsUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    suspend operator fun invoke(): Result<List<Transaction>> =
        repository.getTransactions()
}
