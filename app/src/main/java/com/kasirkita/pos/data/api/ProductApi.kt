package com.kasirkita.pos.data.api

import com.google.gson.JsonObject
import com.kasirkita.pos.data.model.CreateProductRequest
import com.kasirkita.pos.data.model.ProductResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ProductApi {

    @GET("products")
    suspend fun getProducts(
        @Query("q") query: String? = null,
        @Query("category_id") categoryId: String? = null,
        @Query("include_modifiers") includeModifiers: Boolean? = null
    ): List<ProductResponse>

    @POST("products")
    suspend fun createProduct(
        @Body request: CreateProductRequest,
    ): ProductResponse

    @PATCH("products/{id}")
    suspend fun updateProduct(
        @Path("id") productId: String,
        @Body request: JsonObject,
    ): ProductResponse
}
