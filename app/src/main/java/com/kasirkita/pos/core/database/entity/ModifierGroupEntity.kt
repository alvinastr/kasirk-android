package com.kasirkita.pos.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import com.kasirkita.pos.domain.model.ModifierGroup
import com.kasirkita.pos.domain.model.SelectionMode

@Entity(
    tableName = "modifier_groups",
    primaryKeys = ["tenantId", "id"],
    indices = [Index(value = ["tenantId"])],
)
data class ModifierGroupEntity(
    val id: String,
    val tenantId: String,
    val name: String,
    val isActive: Boolean,
)

fun ModifierGroupEntity.toDomain(
    required: Boolean,
    selectionType: String,
    displayOrder: Int,
    options: List<ModifierOptionEntity>,
): ModifierGroup = ModifierGroup(
    id = id,
    tenantId = tenantId,
    name = name,
    isActive = isActive,
    displayOrder = displayOrder,
    required = required,
    selectionType = SelectionMode.valueOf(selectionType),
    options = options.map { it.toDomain() },
)
