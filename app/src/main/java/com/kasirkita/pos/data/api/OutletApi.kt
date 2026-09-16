package com.kasirkita.pos.data.api

import com.kasirkita.pos.data.model.OutletResponse
import retrofit2.http.GET

interface OutletApi {

    @GET("outlets")
    suspend fun getOutlets(): List<OutletResponse>
}
