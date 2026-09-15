package com.kasirkita.pos.data.api

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface TransactionApi {

    @POST("transactions")
    suspend fun createTransaction(
        @Body request: JsonObject,
    ): Response<JsonObject>
}
