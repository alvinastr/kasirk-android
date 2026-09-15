package com.kasirkita.pos.data.api

import com.kasirkita.pos.data.model.ProductResponse
import retrofit2.http.GET

interface ProductApi {

    @GET("products")
    suspend fun getProducts(): List<ProductResponse>
}
