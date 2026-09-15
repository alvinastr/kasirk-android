package com.kasirkita.pos.data.api

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.GET

interface ProductApi {

    @GET("products")
    suspend fun getProducts(): Response<JsonElement>
}
