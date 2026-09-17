package com.kasirkita.pos.data.api

import com.kasirkita.pos.data.model.SyncTransactionsRequest
import com.kasirkita.pos.data.model.SyncTransactionsResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface SyncApi {

    @POST("sync/transactions")
    suspend fun syncTransactions(
        @Body request: SyncTransactionsRequest,
    ): Response<SyncTransactionsResponse>
}
