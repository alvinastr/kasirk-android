package com.kasirkita.pos.data.api

import com.kasirkita.pos.data.model.CreateModifierGroupRequest
import com.kasirkita.pos.data.model.CreateModifierOptionRequest
import com.kasirkita.pos.data.model.ModifierGroupResponse
import com.kasirkita.pos.data.model.ModifierOptionResponse
import com.kasirkita.pos.data.model.UpdateModifierGroupRequest
import com.kasirkita.pos.data.model.UpdateModifierOptionRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ModifierGroupApi {

    @GET("modifier-groups")
    suspend fun getModifierGroups(
        @Query("include_inactive") includeInactive: Boolean? = null
    ): List<ModifierGroupResponse>

    @GET("modifier-groups/{id}")
    suspend fun getModifierGroup(
        @Path("id") groupId: String
    ): ModifierGroupResponse

    @POST("modifier-groups")
    suspend fun createModifierGroup(
        @Body request: CreateModifierGroupRequest
    ): ModifierGroupResponse

    @PATCH("modifier-groups/{id}")
    suspend fun updateModifierGroup(
        @Path("id") groupId: String,
        @Body request: UpdateModifierGroupRequest
    ): ModifierGroupResponse

    @DELETE("modifier-groups/{id}")
    suspend fun deleteModifierGroup(
        @Path("id") groupId: String
    ): Unit

    @POST("modifier-groups/{group_id}/options")
    suspend fun createModifierOption(
        @Path("group_id") groupId: String,
        @Body request: CreateModifierOptionRequest
    ): ModifierOptionResponse

    @PATCH("modifier-groups/{group_id}/options/{option_id}")
    suspend fun updateModifierOption(
        @Path("group_id") groupId: String,
        @Path("option_id") optionId: String,
        @Body request: UpdateModifierOptionRequest
    ): ModifierOptionResponse

    @DELETE("modifier-groups/{group_id}/options/{option_id}")
    suspend fun deleteModifierOption(
        @Path("group_id") groupId: String,
        @Path("option_id") optionId: String
    ): Unit
}