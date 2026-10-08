package com.kasirkita.pos.data.api

import com.kasirkita.pos.data.model.ReceiptSettingsResponse
import com.kasirkita.pos.data.model.UpdateReceiptSettingsRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

interface ReceiptSettingsApi {

    @GET("outlets/{outletId}/receipt-settings")
    suspend fun getReceiptSettings(
        @Path("outletId") outletId: String,
    ): Response<ReceiptSettingsResponse>

    @PUT("outlets/{outletId}/receipt-settings")
    suspend fun putReceiptSettings(
        @Path("outletId") outletId: String,
        @Body request: UpdateReceiptSettingsRequest,
    ): Response<ReceiptSettingsResponse>
}