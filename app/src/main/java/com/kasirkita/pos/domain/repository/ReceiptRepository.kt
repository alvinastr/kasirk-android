package com.kasirkita.pos.domain.repository

import com.kasirkita.pos.domain.model.Receipt

interface ReceiptRepository {
    suspend fun getReceipt(transactionId: String): Result<Receipt>
}
