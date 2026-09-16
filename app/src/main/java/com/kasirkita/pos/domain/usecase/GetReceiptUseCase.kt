package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.repository.ReceiptRepository
import javax.inject.Inject

class GetReceiptUseCase @Inject constructor(
    private val repository: ReceiptRepository,
) {
    suspend operator fun invoke(transactionId: String): Result<Receipt> =
        repository.getReceipt(transactionId)
}
