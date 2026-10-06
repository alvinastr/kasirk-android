package com.kasirkita.pos.data.api

import com.google.gson.JsonObject
import com.kasirkita.pos.data.model.AssignModifierGroupRequest
import com.kasirkita.pos.data.model.CreateProductRequest
import com.kasirkita.pos.data.model.ProductResponse
import com.kasirkita.pos.data.model.ProductModifierAssignmentResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
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

    @GET("products/{product_id}/modifier-groups")
    suspend fun getProductModifierGroups(
        @Path("product_id") productId: String
    ): List<ProductModifierAssignmentResponse>

    @POST("products/{product_id}/modifier-groups")
    suspend fun assignModifierGroup(
        @Path("product_id") productId: String,
        @Body request: AssignModifierGroupRequest
    ): ProductModifierAssignmentResponse

    @PATCH("products/{product_id}/modifier-groups/{group_id}")
    suspend fun updateModifierGroupAssignment(
        @Path("product_id") productId: String,
        @Path("group_id") groupId: String,
        @Body request: AssignModifierGroupRequest
    ): ProductModifierAssignmentResponse

    @DELETE("products/{product_id}/modifier-groups/{group_id}")
    suspend fun removeModifierGroup(
        @Path("product_id") productId: String,
        @Path("group_id") groupId: String
    ): Unit

    @PUT("products/{product_id}/modifier-groups")
    suspend fun replaceModifierGroups(
        @Path("product_id") productId: String,
        @Body request: JsonObject
    ): List<ProductModifierAssignmentResponse>
}