package com.kasirkita.pos.data.api

import com.kasirkita.pos.data.model.CancelHeldOrderRequest
import com.kasirkita.pos.data.model.CheckoutHeldOrderRequest
import com.kasirkita.pos.data.model.CreateHeldOrderRequest
import com.kasirkita.pos.data.model.HeldOrderCheckoutResponse
import com.kasirkita.pos.data.model.HeldOrderResponse
import com.kasirkita.pos.data.model.HeldOrdersResponse
import com.kasirkita.pos.data.model.UpdateHeldOrderRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface HeldOrderApi {

    @POST("held-orders")
    suspend fun createHeldOrder(@Body request: CreateHeldOrderRequest): Response<HeldOrderResponse>

    @GET("held-orders")
    suspend fun getHeldOrders(
        @Query("outlet_id") outletId: String?,
        @Query("status") status: String?,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): Response<HeldOrdersResponse>

    @GET("held-orders/{id}")
    suspend fun getHeldOrder(@Path("id") id: String): Response<HeldOrderResponse>

    @PATCH("held-orders/{id}")
    suspend fun updateHeldOrder(
        @Path("id") id: String,
        @Body request: UpdateHeldOrderRequest,
    ): Response<HeldOrderResponse>

    @POST("held-orders/{id}/cancel")
    suspend fun cancelHeldOrder(
        @Path("id") id: String,
        @Body request: CancelHeldOrderRequest,
    ): Response<HeldOrderResponse>

    @POST("held-orders/{id}/checkout")
    suspend fun checkoutHeldOrder(
        @Path("id") id: String,
        @Body request: CheckoutHeldOrderRequest,
    ): Response<HeldOrderCheckoutResponse>
}