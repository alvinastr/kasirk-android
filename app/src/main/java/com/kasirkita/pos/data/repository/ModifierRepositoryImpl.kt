package com.kasirkita.pos.data.repository

import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.data.api.ModifierGroupApi
import com.kasirkita.pos.data.api.ProductApi
import com.kasirkita.pos.data.local.ModifierLocalDataSource
import com.kasirkita.pos.data.model.AssignModifierGroupRequest
import com.kasirkita.pos.data.model.CreateModifierGroupRequest
import com.kasirkita.pos.data.model.CreateModifierOptionRequest
import com.kasirkita.pos.data.model.ProductModifierAssignmentResponse
import com.kasirkita.pos.data.model.UpdateModifierGroupRequest
import com.kasirkita.pos.data.model.UpdateModifierOptionRequest
import com.kasirkita.pos.data.model.toDomain as groupToDomain
import com.kasirkita.pos.data.model.toDomain as optionToDomain
import com.kasirkita.pos.domain.model.ModifierGroup
import com.kasirkita.pos.domain.model.ModifierOption
import com.kasirkita.pos.domain.model.ProductModifierAssignment
import com.kasirkita.pos.domain.model.SelectionMode
import com.kasirkita.pos.domain.repository.ModifierRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModifierRepositoryImpl @Inject constructor(
    private val modifierGroupApi: ModifierGroupApi,
    private val productApi: ProductApi,
    private val localDataSource: ModifierLocalDataSource,
    private val authSessionDataStore: AuthSessionDataStore,
) : ModifierRepository {

    private suspend fun requireTenantId(): String = authSessionDataStore
        .getSession()
        ?.tenantId
        ?: error("Authenticated session is required")

    override suspend fun getModifierGroups(
        includeInactive: Boolean
    ): Result<List<ModifierGroup>> = runCatching {
        val tenantId = requireTenantId()
        val response = modifierGroupApi.getModifierGroups(includeInactive)
        response.map { it.groupToDomain(tenantId) }
    }

    override suspend fun getModifierGroup(
        groupId: String
    ): Result<ModifierGroup> = runCatching {
        val tenantId = requireTenantId()
        val response = modifierGroupApi.getModifierGroup(groupId)
        response.groupToDomain(tenantId)
    }

    override suspend fun createModifierGroup(
        name: String,
        isActive: Boolean,
        displayOrder: Int
    ): Result<ModifierGroup> = runCatching {
        val tenantId = requireTenantId()
        val response = modifierGroupApi.createModifierGroup(
            CreateModifierGroupRequest(
                name = name,
                isActive = isActive,
                displayOrder = displayOrder,
            )
        )
        response.groupToDomain(tenantId)
    }

    override suspend fun updateModifierGroup(
        groupId: String,
        name: String?,
        isActive: Boolean?,
        displayOrder: Int?
    ): Result<ModifierGroup> = runCatching {
        val tenantId = requireTenantId()
        val response = modifierGroupApi.updateModifierGroup(
            groupId = groupId,
            request = UpdateModifierGroupRequest(
                name = name,
                isActive = isActive,
                displayOrder = displayOrder,
            )
        )
        response.groupToDomain(tenantId)
    }

    override suspend fun deleteModifierGroup(
        groupId: String
    ): Result<Unit> = runCatching {
        modifierGroupApi.deleteModifierGroup(groupId)
    }

    override suspend fun createModifierOption(
        groupId: String,
        name: String,
        priceDelta: Long,
        isActive: Boolean,
        displayOrder: Int
    ): Result<ModifierOption> = runCatching {
        val response = modifierGroupApi.createModifierOption(
            groupId = groupId,
            request = CreateModifierOptionRequest(
                name = name,
                priceDelta = priceDelta,
                isActive = isActive,
                displayOrder = displayOrder,
            )
        )
        response.optionToDomain(groupId)
    }

    override suspend fun updateModifierOption(
        groupId: String,
        optionId: String,
        name: String?,
        priceDelta: Long?,
        isActive: Boolean?,
        displayOrder: Int?
    ): Result<ModifierOption> = runCatching {
        val response = modifierGroupApi.updateModifierOption(
            groupId = groupId,
            optionId = optionId,
            request = UpdateModifierOptionRequest(
                name = name,
                priceDelta = priceDelta,
                isActive = isActive,
                displayOrder = displayOrder,
            )
        )
        response.optionToDomain(groupId)
    }

    override suspend fun deleteModifierOption(
        groupId: String,
        optionId: String
    ): Result<Unit> = runCatching {
        modifierGroupApi.deleteModifierOption(groupId, optionId)
    }

    override suspend fun getProductModifierGroups(
        productId: String
    ): Result<List<ProductModifierAssignment>> = runCatching {
        val response = productApi.getProductModifierGroups(productId)
        response.modifierGroups.map { it.toDomain(response.productId) }
    }

    override suspend fun assignModifierGroup(
        productId: String,
        modifierGroupId: String,
        required: Boolean,
        selectionType: String,
        displayOrder: Int
    ): Result<ProductModifierAssignment> = runCatching {
        val response = productApi.assignModifierGroup(
            productId = productId,
            request = AssignModifierGroupRequest(
                modifierGroupId = modifierGroupId,
                required = required,
                selectionType = selectionType,
                displayOrder = displayOrder,
            )
        )
        response.modifierGroups.first { it.id == modifierGroupId }.toDomain(response.productId)
    }

    override suspend fun updateModifierGroupAssignment(
        productId: String,
        groupId: String,
        modifierGroupId: String,
        required: Boolean,
        selectionType: String,
        displayOrder: Int
    ): Result<ProductModifierAssignment> = runCatching {
        val response = productApi.updateModifierGroupAssignment(
            productId = productId,
            groupId = groupId,
            request = AssignModifierGroupRequest(
                modifierGroupId = modifierGroupId,
                required = required,
                selectionType = selectionType,
                displayOrder = displayOrder,
            )
        )
        response.modifierGroups.first { it.id == modifierGroupId }.toDomain(response.productId)
    }

    override suspend fun removeModifierGroup(
        productId: String,
        groupId: String
    ): Result<Unit> = runCatching {
        productApi.removeModifierGroup(productId, groupId)
    }

    override suspend fun replaceModifierGroups(
        productId: String,
        assignments: List<ProductModifierAssignment>
    ): Result<List<ProductModifierAssignment>> = runCatching {
        val jsonArray = assignments.map { assignment ->
            com.google.gson.JsonObject().apply {
                addProperty("modifier_group_id", assignment.groupId)
                addProperty("required", assignment.required)
                addProperty("selection_type", assignment.selectionMode.name)
                addProperty("display_order", assignment.displayOrder)
            }
        }
        val request = com.google.gson.JsonObject().apply {
            add("modifier_groups", com.google.gson.JsonArray().apply {
                jsonArray.forEach { add(it) }
            })
        }
        val response = productApi.replaceModifierGroups(productId, request)
        response.modifierGroups.map { it.toDomain(response.productId) }
    }

    internal fun ProductModifierAssignmentResponse.toDomain(productId: String): ProductModifierAssignment = ProductModifierAssignment(
        productId = productId,
        groupId = id,
        required = required,
        selectionMode = SelectionMode.valueOf(selectionType),
        displayOrder = displayOrder
    )
}