package com.kasirkita.pos.data.repository

import com.kasirkita.pos.data.api.ReceiptApi
import com.kasirkita.pos.data.model.ReceiptResponse
import com.kasirkita.pos.data.model.toDomain
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.repository.ReceiptRepository
import retrofit2.HttpException
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiptRepositoryImpl @Inject constructor(
    private val receiptApi: ReceiptApi,
) : ReceiptRepository {

    override suspend fun getReceipt(transactionId: String): Result<Receipt> = runCatching {
        val response = receiptApi.getReceipt(transactionId)
        if (!response.isSuccessful) throw HttpException(response)
        response.requireBody().toDomain()
    }

    private fun Response<ReceiptResponse>.requireBody(): ReceiptResponse =
        body() ?: error("Receipt response body is empty")
}
