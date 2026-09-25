package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.Transaction
import com.kasirkita.pos.domain.repository.TransactionRepository
import javax.inject.Inject

class GetTransactionDetailUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    suspend operator fun invoke(transactionId: String): Result<Transaction> =
        repository.getTransactionDetail(transactionId)
}
