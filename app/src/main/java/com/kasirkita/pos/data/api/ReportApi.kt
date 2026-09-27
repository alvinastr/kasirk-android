package com.kasirkita.pos.data.api

import com.kasirkita.pos.data.model.DailySalesResponse
import com.kasirkita.pos.data.model.SalesSummaryResponse
import com.kasirkita.pos.data.model.TopProductResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface ReportApi {

    @GET("reports/sales/daily")
    suspend fun getDailySales(
        @Query("date") date: String,
        @Query("outlet_id") outletId: String?,
    ): DailySalesResponse

    @GET("reports/sales/summary")
    suspend fun getSalesSummary(
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("outlet_id") outletId: String?,
    ): SalesSummaryResponse

    @GET("reports/products/top")
    suspend fun getTopProducts(
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("limit") limit: Int,
        @Query("outlet_id") outletId: String?,
    ): List<TopProductResponse>
}
