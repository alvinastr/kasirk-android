package com.kasirkita.pos.data.api

import com.kasirkita.pos.data.model.CreateTransactionRequest
import com.kasirkita.pos.data.model.TransactionDetailResponse
import com.kasirkita.pos.data.model.TransactionsResponse
import com.kasirkita.pos.data.model.V1CreateTransactionRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

interface TransactionApi {

    @POST("transactions")
    suspend fun createTransaction(
        @Body request: CreateTransactionRequest,
    ): Response<TransactionDetailResponse>

    @POST("transactions")
    suspend fun createV1Transaction(
        @Body request: V1CreateTransactionRequest,
    ): Response<TransactionDetailResponse>

    @GET("transactions")
    suspend fun getTransactions(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): TransactionsResponse

    @GET("transactions/{id}")
    suspend fun getTransactionDetail(
        @Path("id") transactionId: String,
    ): TransactionDetailResponse
}
