package com.kasirkita.pos.data.api

import com.kasirkita.pos.data.model.CategoryResponse
import retrofit2.http.GET

interface CategoryApi {

    @GET("categories")
    suspend fun getCategories(): List<CategoryResponse>
}
