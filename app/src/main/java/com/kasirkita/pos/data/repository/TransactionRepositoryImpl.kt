package com.kasirkita.pos.data.repository

import com.kasirkita.pos.data.api.TransactionApi
import com.kasirkita.pos.data.model.CreateTransactionItemRequest
import com.kasirkita.pos.data.model.CreateTransactionRequest
import com.kasirkita.pos.data.model.PaymentRequest
import com.kasirkita.pos.data.model.TransactionResponse
import com.kasirkita.pos.data.model.toDomain
import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.Transaction
import com.kasirkita.pos.domain.repository.TransactionRepository
import retrofit2.HttpException
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val transactionApi: TransactionApi,
) : TransactionRepository {

    override suspend fun createTransaction(
        clientTransactionId: String,
        outletId: String,
        customerId: String?,
        items: List<CartItem>,
        paymentAmount: Long,
    ): Result<Transaction> = runCatching {
        val response = transactionApi.createTransaction(
            CreateTransactionRequest(
                clientTransactionId = clientTransactionId,
                outletId = outletId,
                customerId = customerId,
                items = items.map { item ->
                    CreateTransactionItemRequest(
                        productId = item.productId,
                        quantity = item.quantity,
                    )
                },
                payment = PaymentRequest(
                    method = PAYMENT_METHOD_CASH,
                    amount = paymentAmount,
                ),
            ),
        )

        if (!response.isSuccessful) throw HttpException(response)
        response.requireBody().toDomain()
    }

    private fun Response<TransactionResponse>.requireBody(): TransactionResponse =
        body() ?: error("Transaction response body is empty")

    private companion object {
        const val PAYMENT_METHOD_CASH = "CASH"
    }
}
