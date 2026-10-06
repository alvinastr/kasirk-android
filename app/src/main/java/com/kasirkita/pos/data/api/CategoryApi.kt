package com.kasirkita.pos.data.api

import com.kasirkita.pos.data.model.CategoryResponse
import com.kasirkita.pos.data.model.CreateCategoryRequest
import com.kasirkita.pos.data.model.UpdateCategoryRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface CategoryApi {

    @GET("categories")
    suspend fun getCategories(): List<CategoryResponse>

    @POST("categories")
    suspend fun createCategory(@Body request: CreateCategoryRequest): Response<CategoryResponse>

    @PATCH("categories/{id}")
    suspend fun updateCategory(
        @Path("id") id: String,
        @Body request: UpdateCategoryRequest,
    ): Response<CategoryResponse>

    @DELETE("categories/{id}")
    suspend fun deleteCategory(@Path("id") id: String): Response<Unit>
}
