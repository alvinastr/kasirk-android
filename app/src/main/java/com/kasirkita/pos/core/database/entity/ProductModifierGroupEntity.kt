package com.kasirkita.pos.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "product_modifier_groups",
    primaryKeys = ["tenantId", "productId", "modifierGroupId"],
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["tenantId", "id"],
            childColumns = ["tenantId", "productId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ModifierGroupEntity::class,
            parentColumns = ["tenantId", "id"],
            childColumns = ["tenantId", "modifierGroupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["tenantId", "productId"]),
        Index(value = ["tenantId", "modifierGroupId"]),
    ],
)
data class ProductModifierGroupEntity(
    val tenantId: String,
    val productId: String,
    val modifierGroupId: String,
    val required: Boolean,
    val selectionType: String,
    val displayOrder: Int,
)
