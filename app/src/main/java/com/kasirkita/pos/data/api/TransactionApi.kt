package com.kasirkita.pos.data.api

import com.kasirkita.pos.data.model.CreateTransactionRequest
import com.kasirkita.pos.data.model.TransactionResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface TransactionApi {

    @POST("transactions")
    suspend fun createTransaction(
        @Body request: CreateTransactionRequest,
    ): Response<TransactionResponse>
}
