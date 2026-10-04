package com.kasirkita.pos.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.kasirkita.pos.domain.model.ModifierOption

@Entity(
    tableName = "modifier_options",
    primaryKeys = ["tenantId", "id"],
    foreignKeys = [ForeignKey(
        entity = ModifierGroupEntity::class,
        parentColumns = ["tenantId", "id"],
        childColumns = ["tenantId", "modifierGroupId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["tenantId", "modifierGroupId"])],
)
data class ModifierOptionEntity(
    val id: String,
    val tenantId: String,
    val modifierGroupId: String,
    val name: String,
    val priceDelta: Long,
    val isActive: Boolean,
    val displayOrder: Int,
)

fun ModifierOptionEntity.toDomain(): ModifierOption = ModifierOption(
    id = id,
    groupId = modifierGroupId,
    name = name,
    priceDelta = priceDelta,
    isActive = isActive,
    displayOrder = displayOrder,
)
