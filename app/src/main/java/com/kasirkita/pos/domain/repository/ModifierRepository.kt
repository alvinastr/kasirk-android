package com.kasirkita.pos.domain.repository

import com.kasirkita.pos.domain.model.ModifierGroup
import com.kasirkita.pos.domain.model.ModifierOption
import com.kasirkita.pos.domain.model.ProductModifierAssignment

interface ModifierRepository {
    suspend fun getModifierGroups(
        includeInactive: Boolean = false
    ): Result<List<ModifierGroup>>

    suspend fun getModifierGroup(
        groupId: String
    ): Result<ModifierGroup>

    suspend fun createModifierGroup(
        name: String,
        isActive: Boolean = true,
        displayOrder: Int = 0
    ): Result<ModifierGroup>

    suspend fun updateModifierGroup(
        groupId: String,
        name: String? = null,
        isActive: Boolean? = null,
        displayOrder: Int? = null
    ): Result<ModifierGroup>

    suspend fun deleteModifierGroup(
        groupId: String
    ): Result<Unit>

    suspend fun createModifierOption(
        groupId: String,
        name: String,
        priceDelta: Long = 0,
        isActive: Boolean = true,
        displayOrder: Int = 0
    ): Result<ModifierOption>

    suspend fun updateModifierOption(
        groupId: String,
        optionId: String,
        name: String? = null,
        priceDelta: Long? = null,
        isActive: Boolean? = null,
        displayOrder: Int? = null
    ): Result<ModifierOption>

    suspend fun deleteModifierOption(
        groupId: String,
        optionId: String
    ): Result<Unit>

    suspend fun getProductModifierGroups(
        productId: String
    ): Result<List<ProductModifierAssignment>>

    suspend fun assignModifierGroup(
        productId: String,
        modifierGroupId: String,
        required: Boolean,
        selectionType: String,
        displayOrder: Int
    ): Result<ProductModifierAssignment>

    suspend fun updateModifierGroupAssignment(
        productId: String,
        groupId: String,
        modifierGroupId: String,
        required: Boolean,
        selectionType: String,
        displayOrder: Int
    ): Result<ProductModifierAssignment>

    suspend fun removeModifierGroup(
        productId: String,
        groupId: String
    ): Result<Unit>

    suspend fun replaceModifierGroups(
        productId: String,
        assignments: List<ProductModifierAssignment>
    ): Result<List<ProductModifierAssignment>>
}