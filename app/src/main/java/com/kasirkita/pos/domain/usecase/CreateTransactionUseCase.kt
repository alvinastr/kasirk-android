package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.Transaction
import com.kasirkita.pos.domain.repository.TransactionRepository
import java.util.UUID
import javax.inject.Inject

class CreateTransactionUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    suspend operator fun invoke(
        outletId: String,
        items: List<CartItem>,
        paymentAmount: Long,
        customerId: String? = null,
    ): Result<Transaction> = repository.createTransaction(
        clientTransactionId = UUID.randomUUID().toString(),
        outletId = outletId,
        customerId = customerId,
        items = items,
        paymentAmount = paymentAmount,
    )
}
