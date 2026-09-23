package com.kasirkita.pos.data.api

import com.kasirkita.pos.data.model.CreateStockAdjustmentRequest
import com.kasirkita.pos.data.model.StockResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface StockApi {

    @GET("stock/{outlet_id}")
    suspend fun getStocks(
        @Path("outlet_id") outletId: String,
    ): List<StockResponse>

    @POST("stock/adjustment")
    suspend fun createAdjustment(
        @Body request: CreateStockAdjustmentRequest,
    ): StockResponse
}
