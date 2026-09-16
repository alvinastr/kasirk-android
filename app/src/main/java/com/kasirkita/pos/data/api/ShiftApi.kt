package com.kasirkita.pos.data.api

import com.kasirkita.pos.data.model.CloseShiftRequest
import com.kasirkita.pos.data.model.OpenShiftRequest
import com.kasirkita.pos.data.model.ShiftResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ShiftApi {

    @GET("shifts/current")
    suspend fun getCurrentShift(): Response<ShiftResponse>

    @POST("shifts/open")
    suspend fun openShift(
        @Body request: OpenShiftRequest,
    ): Response<ShiftResponse>

    @POST("shifts/{id}/close")
    suspend fun closeShift(
        @Path("id") shiftId: String,
        @Body request: CloseShiftRequest,
    ): Response<ShiftResponse>
}
