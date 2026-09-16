package com.kasirkita.pos.data.api

import com.kasirkita.pos.data.model.ReceiptResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface ReceiptApi {

    @GET("receipts/{transaction_id}")
    suspend fun getReceipt(
        @Path("transaction_id") transactionId: String,
    ): Response<ReceiptResponse>
}
